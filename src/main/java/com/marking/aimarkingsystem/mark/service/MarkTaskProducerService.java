package com.marking.aimarkingsystem.mark.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.common.config.RabbitMQConfig;
import com.marking.aimarkingsystem.common.util.CurrentUserUtil;
import com.marking.aimarkingsystem.mark.dto.MarkTaskMessage;
import com.marking.aimarkingsystem.mark.entity.MarkTask;
import com.marking.aimarkingsystem.mark.mapper.MarkTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 异步任务消息生产者 Service（模块4）。
 *
 * 职责：
 * 1. 接收业务侧的"要做 AI 阅卷 / OCR 识别"请求，先生成 taskNo、落库 mark_task（status=0 待消费）；
 * 2. 把 MarkTaskMessage(taskNo) 发到对应 MQ 队列（AI 阅卷 → marking.ai.queue，OCR → marking.ocr.queue）；
 * 3. 投递异常回写 mark_task.status=3（失败待重试），ConfirmCallback / ReturnCallback 异步失败也回写。
 *
 * 消息体极简（只含 taskNo）：消费者拿到后按 taskNo 查 mark_task → exam_answer，
 * 完整的答卷内容、OCR objectKey、试卷 Prompt 模板等全在 DB 里。
 *
 * 幂等策略：
 * 同一个 answerId + taskType 存在"未终态"任务（status in 0/1/3）时，直接返回已有 taskNo，
 * 不再重复发 MQ。status=2（成功）和 status=4（死信终态）的重试由后续阶段单独方法处理。
 *
 * 与模块3的对接点：
 * - AnswerService.submitOnline 成功后调 submitAiMark(answerId) 投递 AI 阅卷；
 * - PaperAnswerService.triggerOcr 成功后调 submitOcr(answerId) 投递 OCR。
 * 本类只负责"发"，不负责"消费"——消费逻辑留给模块4-阶段4实现。
 */
@Service
public class MarkTaskProducerService {

    private static final Logger log = LoggerFactory.getLogger(MarkTaskProducerService.class);

    /** 任务类型常量，避免散字符串 */
    public static final String TASK_TYPE_AI_MARK = "AI_MARK";
    public static final String TASK_TYPE_OCR = "OCR";

    /** 未终态状态码集合：待消费 / 处理中 / 失败待重试，消费者可能还要处理 */
    private static final List<Integer> INCOMPLETE_STATUSES = Arrays.asList(0, 1, 3);

    private final MarkTaskMapper markTaskMapper;
    private final RabbitTemplate rabbitTemplate;

    public MarkTaskProducerService(MarkTaskMapper markTaskMapper, RabbitTemplate rabbitTemplate) {
        this.markTaskMapper = markTaskMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ==================== 业务入口 ====================

    /**
     * 投递 AI 阅卷任务。
     * 调用时机：线上答卷提交成功（AnswerService.submitOnline）、纸质答卷 OCR 完成后。
     *
     * @param answerId 答卷ID
     * @return 任务编号（UUID）；幂等命中时返回已有 taskNo
     */
    @Transactional(rollbackFor = Exception.class)
    public String submitAiMark(Long answerId) {
        // BYOK：记录是哪个用户触发的阅卷，消费者据此取该用户的 Key 调用大模型。
        // 异步线程（如定时补偿）里取不到登录上下文，返回 null 时消费者回退到最近一次保存的 Key。
        String llmUser = CurrentUserUtil.usernameOrNull();
        return createAndSend(answerId, TASK_TYPE_AI_MARK,
                RabbitMQConfig.ROUTING_AI, llmUser);
    }

    /**
     * 投递 OCR 识别任务。
     * 调用时机：纸质答卷上传扫描图片后、老师触发 OCR。
     *
     * @param answerId 答卷ID
     * @return 任务编号（UUID）；幂等命中时返回已有 taskNo
     */
    @Transactional(rollbackFor = Exception.class)
    public String submitOcr(Long answerId) {
        return createAndSend(answerId, TASK_TYPE_OCR,
                RabbitMQConfig.ROUTING_OCR, null);
    }

    // ==================== 核心实现 ====================

    /**
     * 先落库 mark_task(status=0)，再发 MQ；幂等检查 + 同步异常回写 + 异步回调兜底。
     *
     * 投递可靠性三件套在本方法的落地：
     * - 落库先于发 MQ：消息发出去了但 DB 失败 → 抛异常事务回滚，不会出现"MQ 里有消息但 mark_task 里没记录"的孤儿消息；
     * - convertAndSend 同步异常 catch：RabbitMQ 连接没建立、序列化失败等，直接回写 status=3；
     * - CorrelationData 带 taskNo：异步 ConfirmCallback/ReturnCallback 回调时能定位 mark_task。
     */
    private String createAndSend(Long answerId, String taskType, String routingKey, String llmUser) {
        // ---- 幂等：查是否已有未终态任务 ----
        MarkTask existing = markTaskMapper.selectOne(Wrappers.<MarkTask>lambdaQuery()
                .eq(MarkTask::getAnswerId, answerId)
                .eq(MarkTask::getTaskType, taskType)
                .in(MarkTask::getStatus, INCOMPLETE_STATUSES)
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("幂等命中，跳过重复投递: answerId={}, taskType={}, existingTaskNo={}",
                    answerId, taskType, existing.getTaskNo());
            return existing.getTaskNo();
        }

        // ---- 落库 mark_task(status=0) ----
        String taskNo = IdUtil.fastSimpleUUID();
        MarkTask task = new MarkTask();
        task.setTaskNo(taskNo);
        task.setAnswerId(answerId);
        task.setTaskType(taskType);
        task.setStatus(0);          // 待消费
        task.setRetryCount(0);
        task.setErrorMsg(null);
        markTaskMapper.insert(task);
        log.info("异步任务落库成功: taskNo={}, answerId={}, taskType={}, status=0",
                taskNo, answerId, taskType);

        // ---- 发 MQ，同步异常回写 ----
        try {
            MarkTaskMessage message = new MarkTaskMessage(taskNo);
            // CorrelationData 携带 taskNo，ConfirmCallback/ReturnCallback 回调时能定位 mark_task
            CorrelationData correlationData = new CorrelationData(taskNo);
            // BYOK：把触发人用户名放进消息头（不塞进消息体，保持消息体仅含 taskNo 的极简设计）
            MessagePostProcessor llmUserHeader = msg -> {
                if (llmUser != null) {
                    msg.getMessageProperties().setHeader("llmUser", llmUser);
                }
                return msg;
            };
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_MARKING_TASK,
                    routingKey,
                    message,
                    llmUserHeader,
                    correlationData);
            log.info("异步任务消息已发送: taskNo={}, exchange={}, routingKey={}",
                    taskNo, RabbitMQConfig.EXCHANGE_MARKING_TASK, routingKey);
        } catch (Exception e) {
            // 同步异常：RabbitMQ 连接失败、序列化失败等 → 消息肯定没发出去
            log.error("异步任务 MQ 发送同步异常: taskNo={}, error={}", taskNo, e.getMessage(), e);
            markTaskMapper.update(null, Wrappers.<MarkTask>lambdaUpdate()
                    .eq(MarkTask::getTaskNo, taskNo)
                    .set(MarkTask::getStatus, 3)               // 失败待重试
                    .set(MarkTask::getRetryCount, 1)
                    .set(MarkTask::getErrorMsg, truncate("MQ发送异常: " + e.getMessage())));
            throw new RuntimeException("异步任务投递失败: " + e.getMessage(), e);
        }

        return taskNo;
    }

    // ==================== 供 RabbitMQConfig ConfirmCallback / ReturnCallback 调用 ====================

    /**
     * 异步投递失败回写（供 RabbitMQConfig ConfirmCallback / ReturnCallback 回调）。
     *
     * 只回写 status 仍在"待消费/处理中"的任务（消费者处理过的任务状态 ≥ 2，不覆盖）。
     * retry_count 自增用原生 SQL 片段 setSql("retry_count = retry_count + 1")，
     * 避免查库 → +1 → 写回的并发竞态（两个异步回调可能同时触发）。
     *
     * 为什么由 ProducerService 而非 RabbitMQConfig 注入 Mapper 做？
     * ProducerService 是业务入口，知道哪些状态该回写、回写哪些列；
     * RabbitMQConfig 只管 MQ 拓扑声明，不掺业务语义。
     */
    public void onPublishFail(String taskNo, String reason) {
        if (taskNo == null || taskNo.isEmpty()) {
            return;
        }
        int rows = markTaskMapper.update(null, Wrappers.<MarkTask>lambdaUpdate()
                .eq(MarkTask::getTaskNo, taskNo)
                .in(MarkTask::getStatus, 0, 1)   // 只回写"还没被消费者处理"的任务
                .setSql("retry_count = retry_count + 1")
                .set(MarkTask::getStatus, 3)
                .set(MarkTask::getErrorMsg, truncate("MQ投递失败: " + reason)));
        if (rows > 0) {
            log.warn("异步任务异步投递失败已回写: taskNo={}, reason={}", taskNo, reason);
        } else {
            // 状态已被消费者推进，ConfirmCallback 只是晚到了，幂等忽略
            log.info("异步任务异步投递失败已忽略（任务状态已非待消费）: taskNo={}", taskNo);
        }
    }

    // ==================== 工具方法 ====================

    /** error_msg 列 VARCHAR(2000)，超长截断 */
    private String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() > 2000 ? msg.substring(0, 2000) : msg;
    }
}
