package com.marking.aimarkingsystem.mark.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.common.config.RabbitMQConfig;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.util.CurrentUserUtil;
import com.marking.aimarkingsystem.mark.consumer.MarkTaskConsumerSupport;
import com.marking.aimarkingsystem.mark.dto.MarkTaskMessage;
import com.marking.aimarkingsystem.mark.entity.MarkTask;
import com.marking.aimarkingsystem.mark.mapper.MarkTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 异步任务补偿服务：手动重投 + 定时扫描超时任务。
 *
 * 解决的问题：
 * - 消费者 nack 后消息进入死信队列，而 DLQ 只打日志不重投，失败任务此前只能人工查表；
 * - 进程被 kill 或消息丢失时，mark_task 会永远停在 status=0/1，没人推进。
 *
 * 两个入口：
 * 1. {@link #retry(Long)}：管理员/教师对失败(3)或死信(4)任务手动重投；
 * 2. {@link #recoverStaleTasks()}：定时扫描超过阈值仍停在待消费/处理中的任务，自动重新投递。
 *
 * 并发安全：多实例同时扫描时，靠状态 CAS（id + status in (0,1)）保证同一任务只被一个实例重投；
 * 消费者侧还有 Redis 锁 + 状态 CAS 幂等，重复投递不会导致重复打分。
 */
@Slf4j
@Service
public class MarkTaskRecoveryService {

    /** 单次扫描最多处理的任务数，避免一次捞太多 */
    private static final int SCAN_BATCH_SIZE = 20;

    private final MarkTaskMapper markTaskMapper;
    private final RabbitTemplate rabbitTemplate;

    /** 停在待消费/处理中的任务超过该时长视为执行中断 */
    @Value("${mark.task-timeout-minutes:15}")
    private long taskTimeoutMinutes;

    /** 失败待重试(status=3)的任务冷却多久后再自动重投，避免刚失败就立刻重试 */
    @Value("${mark.retry-backoff-minutes:3}")
    private long retryBackoffMinutes;

    public MarkTaskRecoveryService(MarkTaskMapper markTaskMapper, RabbitTemplate rabbitTemplate) {
        this.markTaskMapper = markTaskMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 手动重试失败/死信任务：重置为待消费并重新投递。
     *
     * @param taskId mark_task 主键
     * @return 任务编号
     */
    public String retry(Long taskId) {
        MarkTask task = markTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if (Objects.equals(task.getStatus(), 2)) {
            throw new BusinessException("任务已成功，无需重试");
        }
        if (Objects.equals(task.getStatus(), 0) || Objects.equals(task.getStatus(), 1)) {
            throw new BusinessException("任务正在处理中，请稍后再试");
        }

        int rows = markTaskMapper.update(null, Wrappers.<MarkTask>lambdaUpdate()
                .eq(MarkTask::getId, taskId)
                .in(MarkTask::getStatus, 3, 4)
                .set(MarkTask::getStatus, 0)
                .set(MarkTask::getRetryCount, 0)
                .set(MarkTask::getErrorMsg, null)
                .set(MarkTask::getStartTime, null)
                .set(MarkTask::getFinishTime, null));
        if (rows == 0) {
            throw new BusinessException("任务状态已变化，请刷新后重试");
        }

        publish(task, CurrentUserUtil.usernameOrNull());
        log.info("手动重试任务已重新投递: taskId={}, taskNo={}, taskType={}",
                taskId, task.getTaskNo(), task.getTaskType());
        return task.getTaskNo();
    }

    /**
     * 定时补偿，分两类：
     * 1. status=0/1 长时间无进展：进程被杀、消息丢失、消费卡死 → 重置重投；
     * 2. status=3 失败待重试：冷却期过后自动重投，受 MAX_RETRY 限制；
     * status=4 是已用完重试次数的死信终态，不自动重投（多为毒消息），只允许手动排查后重投。
     */
    @Scheduled(fixedDelayString = "${mark.recovery-interval-ms:300000}",
            initialDelayString = "${mark.recovery-initial-delay-ms:60000}")
    public void recoverStaleTasks() {
        LocalDateTime now = LocalDateTime.now();
        recoverStuckTasks(now);
        retryFailedTasks(now);
    }

    private void recoverStuckTasks(LocalDateTime now) {
        LocalDateTime deadline = now.minusMinutes(taskTimeoutMinutes);
        List<MarkTask> staleTasks = markTaskMapper.selectList(Wrappers.<MarkTask>lambdaQuery()
                .in(MarkTask::getStatus, 0, 1)
                .lt(MarkTask::getUpdateTime, deadline)
                .orderByAsc(MarkTask::getUpdateTime)
                .last("LIMIT " + SCAN_BATCH_SIZE));
        for (MarkTask task : staleTasks) {
            recover(task);
        }
    }

    /**
     * 自动重投失败任务：这是消费端 nack 后真正需要的重试入口。
     * 没有它，retry_count 的 3 次上限形同虚设——第一次失败进死信后任务就永远停在 3。
     */
    private void retryFailedTasks(LocalDateTime now) {
        LocalDateTime deadline = now.minusMinutes(retryBackoffMinutes);
        List<MarkTask> failedTasks = markTaskMapper.selectList(Wrappers.<MarkTask>lambdaQuery()
                .eq(MarkTask::getStatus, 3)
                .lt(MarkTask::getUpdateTime, deadline)
                .orderByAsc(MarkTask::getUpdateTime)
                .last("LIMIT " + SCAN_BATCH_SIZE));
        for (MarkTask task : failedTasks) {
            int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
            if (retryCount >= MarkTaskConsumerSupport.MAX_RETRY) {
                // 次数已用完：转死信终态，避免无限重试
                int rows = markTaskMapper.update(null, Wrappers.<MarkTask>lambdaUpdate()
                        .eq(MarkTask::getId, task.getId())
                        .eq(MarkTask::getStatus, 3)
                        .set(MarkTask::getStatus, 4)
                        .set(MarkTask::getErrorMsg, "重试次数已达上限，转死信待人工处理")
                        .set(MarkTask::getFinishTime, LocalDateTime.now()));
                if (rows > 0) {
                    log.error("失败任务重试次数已用尽，转死信: taskNo={}, retryCount={}",
                            task.getTaskNo(), retryCount);
                }
                continue;
            }
            int rows = markTaskMapper.update(null, Wrappers.<MarkTask>lambdaUpdate()
                    .eq(MarkTask::getId, task.getId())
                    .eq(MarkTask::getStatus, 3)
                    .set(MarkTask::getStatus, 0)
                    .set(MarkTask::getStartTime, null)
                    .set(MarkTask::getErrorMsg, "失败后自动重试第 " + (retryCount + 1) + " 次"));
            if (rows == 0) {
                continue;
            }
            publish(task, null);
            log.warn("失败任务自动重投: taskNo={}, taskType={}, retryCount={}",
                    task.getTaskNo(), task.getTaskType(), retryCount);
        }
    }

    private void recover(MarkTask task) {
        int nextRetry = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        if (nextRetry > MarkTaskConsumerSupport.MAX_RETRY) {
            int rows = markTaskMapper.update(null, Wrappers.<MarkTask>lambdaUpdate()
                    .eq(MarkTask::getId, task.getId())
                    .in(MarkTask::getStatus, 0, 1)
                    .set(MarkTask::getStatus, 4)
                    .set(MarkTask::getErrorMsg, "执行超时且超过重试上限，已放弃")
                    .set(MarkTask::getFinishTime, LocalDateTime.now()));
            if (rows > 0) {
                log.error("任务超时且超过重试上限，转死信终态: taskNo={}, retryCount={}",
                        task.getTaskNo(), task.getRetryCount());
            }
            return;
        }

        int rows = markTaskMapper.update(null, Wrappers.<MarkTask>lambdaUpdate()
                .eq(MarkTask::getId, task.getId())
                .in(MarkTask::getStatus, 0, 1)
                .set(MarkTask::getStatus, 0)
                .set(MarkTask::getRetryCount, nextRetry)
                .set(MarkTask::getErrorMsg, "执行超时，已自动重新投递")
                .set(MarkTask::getStartTime, null));
        if (rows == 0) {
            // 其他实例已处理，跳过
            return;
        }

        publish(task, null);
        log.warn("检测到超时任务，已自动重新投递: taskNo={}, taskType={}, retryCount={}",
                task.getTaskNo(), task.getTaskType(), nextRetry);
    }

    /**
     * 重新投递消息。
     * 定时补偿场景没有登录上下文，llmUser 传 null，消费者会回退到最近一次保存的 Key。
     */
    private void publish(MarkTask task, String llmUser) {
        String routingKey = MarkTaskProducerService.TASK_TYPE_OCR.equals(task.getTaskType())
                ? RabbitMQConfig.ROUTING_OCR
                : RabbitMQConfig.ROUTING_AI;
        MessagePostProcessor headerProcessor = msg -> {
            if (llmUser != null) {
                msg.getMessageProperties().setHeader("llmUser", llmUser);
            }
            return msg;
        };
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_MARKING_TASK,
                routingKey,
                new MarkTaskMessage(task.getTaskNo()),
                headerProcessor,
                new CorrelationData(task.getTaskNo()));
    }
}
