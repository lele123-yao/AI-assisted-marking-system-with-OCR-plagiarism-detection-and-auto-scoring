package com.marking.aimarkingsystem.mark.consumer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.mark.entity.MarkTask;
import com.marking.aimarkingsystem.mark.mapper.MarkTaskMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * MQ 消费者基类：封装手动 ACK + 失败死信 + mark_task 状态回写。
 *
 * 两个子类（AI阅卷 / OCR 识别）共享：
 * 1. 幂等查询 mark_task（按 taskNo 查，已是终态 status≥2 直接 ack 跳过）；
 * 2. 处理成功：mark_task status=2 + finish_time，basicAck；
 * 3. 处理失败：retry_count < 3 时 status=3 + retry_count++ + basicNack(不重投,不丢)；
 *    retry_count ≥ 3 时 status=4 + basicNack(不重投,丢) —— 消息进死信队列；
 * 4. 任意异常都**不重投原队列**，全部走死信兜底。
 *
 * 为什么 basicNack(requeue=false) 而非 requeue=true？
 * requeue=true 会把消息塞回队列同位置，若异常是代码 bug 或数据脏污（如答卷已删），
 * 消息会无限 requeue 阻塞后续消息。本项目把"重试"放到 mark_task.retry_count 里，
 * 消费端只在 retry_count < 3 时把消息丢回死信队列，后续由死信队列消费者决定是否手动补偿。
 * 这样队列永远不会因毒消息而阻塞。
 */
public abstract class MarkTaskConsumerSupport {

    private static final Logger log = LoggerFactory.getLogger(MarkTaskConsumerSupport.class);

    /** 最大重试次数：3。超过后任务进死信队列，不再自动重试 */
    public static final int MAX_RETRY = 3;

    protected final MarkTaskMapper markTaskMapper;

    protected MarkTaskConsumerSupport(MarkTaskMapper markTaskMapper) {
        this.markTaskMapper = markTaskMapper;
    }

    // ==================== 1. 幂等查询 ====================

    /**
     * 按 taskNo 查 mark_task。找不到 → 任务丢了但我们不该 ack（让它走 retry/死信），
     * 但实际上 mark_task 总是先 insert 再发 MQ，找不到说明 DB 有问题，
     * 此时让消息 nack + 进死信，任务留在 mark_task 等人工排查。
     */
    protected MarkTask requireTask(String taskNo) {
        MarkTask task = markTaskMapper.selectOne(Wrappers.<MarkTask>lambdaQuery()
                .eq(MarkTask::getTaskNo, taskNo));
        if (task == null) {
            throw new IllegalStateException("mark_task 不存在: " + taskNo);
        }
        return task;
    }

    /**
     * 是否已处于终态（成功 2 / 死信 4）：若是，说明之前已经处理过，直接 ack 幂等跳过。
     * 这保证了"消息重复投递"不会导致业务被执行两遍。
     */
    protected boolean isTerminal(MarkTask task) {
        Integer status = task.getStatus();
        return status != null && (status == 2 || status == 4);
    }

    // ==================== 2. 手动 ACK ====================

    /** 处理成功 ack：basicAck(false) 不批量 ack，只确认当前这条 */
    protected void ack(Channel channel, long deliveryTag, MarkTask task) throws IOException {
        channel.basicAck(deliveryTag, false);
        log.info("[{}] 消费成功已 ack: taskNo={}", getTaskType(), task.getTaskNo());
    }

    /** 处理失败 nack：requeue=false 让消息进死信队列，不原地重投 */
    protected void nackToDlq(Channel channel, long deliveryTag, MarkTask task, String reason) throws IOException {
        // requeue=false → 消息不会回到当前队列；队列配置了 x-dead-letter-exchange → 进 DLX → DLQ
        channel.basicNack(deliveryTag, false, false);
        log.warn("[{}] 消费失败已 nack 到 DLQ: taskNo={}, reason={}",
                getTaskType(), task == null ? "?" : task.getTaskNo(), reason);
    }

    // ==================== 3. mark_task 状态回写 ====================

    /**
     * 成功回写：status=2 处理中→成功，finish_time=now，start_time=now（若没写过）。
     */
    protected void markSuccess(MarkTask task) {
        MarkTask update = new MarkTask();
        update.setId(task.getId());
        update.setStatus(2);
        update.setFinishTime(LocalDateTime.now());
        if (task.getStartTime() == null) {
            update.setStartTime(LocalDateTime.now());
        }
        markTaskMapper.updateById(update);
    }

    /**
     * 开始处理：status=1 + start_time。
     * 幂等跳过：若已是 status=1/2/4 则不写（可能其他消费者已处理）。
     */
    protected boolean markProcessingIfIdle(MarkTask task) {
        int rows = markTaskMapper.update(null, Wrappers.<MarkTask>lambdaUpdate()
                .eq(MarkTask::getId, task.getId())
                .in(MarkTask::getStatus, 0, 3)  // 只从"待消费/失败待重试"推进
                .set(MarkTask::getStatus, 1)
                .set(MarkTask::getStartTime, LocalDateTime.now()));
        return rows > 0;
    }

    /**
     * 失败回写。
     * retry_count < MAX_RETRY → status=3 失败待重试；
     * retry_count ≥ MAX_RETRY → status=4 已入死信（进终态，等人工处理）。
     * retry_count 用 SQL setSql 原子自增，防并发。
     */
    protected void markFailed(MarkTask task, String reason) {
        boolean finalFail = task.getRetryCount() != null && task.getRetryCount() >= MAX_RETRY;
        int newStatus = finalFail ? 4 : 3;
        markTaskMapper.update(null, Wrappers.<MarkTask>lambdaUpdate()
                .eq(MarkTask::getId, task.getId())
                .setSql("retry_count = retry_count + 1")
                .set(MarkTask::getStatus, newStatus)
                .set(MarkTask::getErrorMsg, truncate(reason))
                .set(MarkTask::getFinishTime, LocalDateTime.now()));
        log.warn("[{}] mark_task 失败回写: taskNo={}, retryCount={}, finalFail={}",
                getTaskType(), task.getTaskNo(), task.getRetryCount(), finalFail);
    }

    // ==================== Sentinel 限流/熔断回调 ====================

    /**
     * Sentinel 拦截（限流/熔断）时的统一处理。
     *
     * 为什么不直接把消息 requeue？
     * 被限流说明系统正处于过载状态，原地 requeue 会让消息立刻回到队列头部，
     * 形成"取出→被拦截→再入队"的热循环，白白消耗 CPU 且不产生任何进展。
     *
     * 这里的策略是：mark_task 回写失败（retry_count++），消息 nack 进死信队列，
     * 再由定时补偿任务（MarkTaskRecoveryService）在冷却期后重新投递，实现"削峰"而不是"空转"。
     */
    protected void handleBlocked(Message message, Channel channel, String reason) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        MarkTask task = null;
        try {
            task = requireTask(readTaskNo(message));
            markFailed(task, reason);
        } catch (Exception e) {
            // 任务查不到（DB 异常等）时仍然要把消息处理掉，避免它卡在队列里
            log.error("[{}] Sentinel 拦截后回写任务状态失败: {}", getTaskType(), e.getMessage());
        }
        try {
            nackToDlq(channel, deliveryTag, task, reason);
        } catch (IOException e) {
            log.warn("[{}] Sentinel 拦截后 nack 失败，任务已回写状态: {}",
                    getTaskType(), e.getMessage());
        }
    }

    // ==================== 4. 抽象方法 ====================

    /** 子类返回任务类型，用于日志区分 */
    protected abstract String getTaskType();

    // ==================== 工具方法 ====================

    /** message.getBody() → UTF-8 字符串（MarkTaskMessage.taskNo JSON） */
    protected String readTaskNo(Message message) {
        try {
            String body = new String(message.getBody());
            // 消息体是 {"taskNo":"xxx"}，直接正则提取；不用 Hutool JSONUtil 也行
            int start = body.indexOf("\"taskNo\"");
            if (start < 0) {
                throw new RuntimeException("消息体里找不到 taskNo: " + body);
            }
            int colon = body.indexOf(':', start);
            int quote1 = body.indexOf('"', colon + 1);
            int quote2 = body.indexOf('"', quote1 + 1);
            return body.substring(quote1 + 1, quote2);
        } catch (RuntimeException e) {
            log.error("解析消息体失败: {}", e.getMessage());
            throw e;
        }
    }

    protected String truncate(String msg) {
        if (msg == null) return null;
        return msg.length() > 2000 ? msg.substring(0, 2000) : msg;
    }
}
