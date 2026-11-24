package com.marking.aimarkingsystem.mark.consumer;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.answer.entity.ExamAnswer;
import com.marking.aimarkingsystem.answer.enums.AnswerStatusEnum;
import com.marking.aimarkingsystem.answer.mapper.ExamAnswerMapper;
import com.marking.aimarkingsystem.mark.entity.MarkTask;
import com.marking.aimarkingsystem.mark.mapper.MarkTaskMapper;
import com.marking.aimarkingsystem.mark.service.MarkTaskProducerService;
import com.marking.aimarkingsystem.plagiarism.service.PlagiarismService;
import com.marking.aimarkingsystem.common.util.MinioUtil;
import com.marking.aimarkingsystem.common.util.RedisDistributedLock;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OCR 识别消费者（模块4-阶段4）。
 *
 * 职责：
 * 1. 消费 OCR 任务队列的 MarkTaskMessage；
 * 2. 查 exam_answer → 拿到 paperImgUrl（MinIO objectKey）；
 * 3. **调用第三方 OCR 服务**（本阶段方法留好接口，内部打印日志模拟调用；模块5接入真实 OCR 时替换实现）；
 * 4. 把识别结果写回 exam_answer.ocr_text，推进 markingStatus → AI_MARKING（状态 2，等待 AI 阅卷）；
 * 5. 成功 mark_task status=2 + ack；失败 retry_count++ → 超过 MAX_RETRY 进死信队列。
 *
 * Sentinel 占位：@SentinelResource(value="ocrConsumer", blockHandler="onBlocked")。
 */
@Component
public class OcrConsumer extends MarkTaskConsumerSupport {

    private static final Logger log = LoggerFactory.getLogger(OcrConsumer.class);

    /** 分布式锁 Key 前缀：同一 OCR taskNo 同一时刻只允许一个消费者处理 */
    private static final String LOCK_KEY_PREFIX = "lock:mark:ocr:";

    /** 锁租约 5 分钟：OCR 调用 + 自动投递 AI 阅卷任务的最坏耗时 */
    private static final long LOCK_LEASE_MILLIS = 300_000L;

    private final ExamAnswerMapper examAnswerMapper;
    private final MinioUtil minioUtil;
    private final MarkTaskProducerService markTaskProducerService;
    private final PlagiarismService plagiarismService;
    private final RedisDistributedLock distributedLock;

    public OcrConsumer(MarkTaskMapper markTaskMapper,
                       ExamAnswerMapper examAnswerMapper,
                       MinioUtil minioUtil,
                       MarkTaskProducerService markTaskProducerService,
                       PlagiarismService plagiarismService,
                       RedisDistributedLock distributedLock) {
        super(markTaskMapper);
        this.examAnswerMapper = examAnswerMapper;
        this.minioUtil = minioUtil;
        this.markTaskProducerService = markTaskProducerService;
        this.plagiarismService = plagiarismService;
        this.distributedLock = distributedLock;
    }

    @RabbitListener(queues = "${mq.ocr.queue:marking.ocr.queue}")
    @SentinelResource(value = "ocrConsumer", blockHandler = "onBlocked")
    public void consume(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        MarkTask task = null;
        String lockKey = null;
        String lockToken = null;
        try {
            String taskNo = readTaskNo(message);
            log.info("OCR消费者收到消息: taskNo={}", taskNo);

            // ---- 分布式锁占位：同一 taskNo 同一时刻只允许一个实例处理 ----
            // 与 markProcessingIfIdle 的乐观状态更新互补，防止多实例重复调用 OCR 服务。
            lockKey = LOCK_KEY_PREFIX + taskNo;
            lockToken = distributedLock.tryLock(lockKey, LOCK_LEASE_MILLIS);
            if (lockToken == null) {
                log.warn("OCR任务已被其他消费者持有锁，跳过重复消费: taskNo={}", taskNo);
                channel.basicAck(deliveryTag, false);
                return;
            }

            task = requireTask(taskNo);
            if (isTerminal(task)) {
                log.info("OCR任务已是终态，幂等跳过: taskNo={}, status={}", taskNo, task.getStatus());
                ack(channel, deliveryTag, task);
                return;
            }
            if (!markProcessingIfIdle(task)) {
                log.warn("OCR任务状态已被其他消费者推进，放弃处理: taskNo={}", taskNo);
                ack(channel, deliveryTag, task);
                return;
            }

            ExamAnswer answer = examAnswerMapper.selectById(task.getAnswerId());
            if (answer == null) {
                throw new IllegalStateException("答卷不存在: " + task.getAnswerId());
            }
            if (answer.getPaperImgUrl() == null) {
                throw new IllegalStateException("答卷没有扫描图片: answerId=" + answer.getId());
            }

            // ---- 调用 OCR（模块5接入真实服务，本阶段模拟） ----
            String ocrText = callOcr(answer.getPaperImgUrl());

            // ---- 写答卷：ocr_text + markingStatus 推进到 2（AI辅助批改中） ----
            examAnswerMapper.update(null, Wrappers.<ExamAnswer>lambdaUpdate()
                    .eq(ExamAnswer::getId, answer.getId())
                    .set(ExamAnswer::getOcrText, ocrText)
                    .set(ExamAnswer::getMarkingStatus, AnswerStatusEnum.AI_MARKING.getCode())
                    .set(ExamAnswer::getMarkingError, null));

            markSuccess(task);

            // OCR 文本已落库，立即生成查重指纹（纸质答卷与线上答卷在同一指纹空间比对）
            plagiarismService.generateFingerprintQuietly(answer.getId());

            ack(channel, deliveryTag, task);

            // ---- OCR 完成后自动触发 AI 阅卷 ----
            // 形成 OCR → AI 阅卷 的自动流水线。
            // ProducerService 内部幂等：同一答卷的 AI 阅卷任务若已存在未终态则直接返回 taskNo，
            // 所以即使线上答卷已经自己触发过 AI 阅卷（AnswerService.submitOnline）也不会重复。
            try {
                String aiTaskNo = markTaskProducerService.submitAiMark(answer.getId());
                log.info("OCR完成，已自动投递 AI 阅卷任务: answerId={}, aiTaskNo={}", answer.getId(), aiTaskNo);
            } catch (Exception e) {
                // OCR 成功但自动投 AI 失败不应让消息重投（OCR 已经做了）；
                // 只打日志，允许老师后续手动触发 AI 阅卷
                log.error("OCR完成但自动投递 AI 阅卷失败: answerId={}, error={}", answer.getId(), e.getMessage());
            }
        } catch (Exception e) {
            String reason;
            if (e instanceof BlockException) {
                reason = "Sentinel " + e.getClass().getSimpleName();
                log.warn("OCR 被 Sentinel 熔断: {}", reason);
            } else {
                reason = e.getMessage();
                log.error("OCR 消费异常: taskNo={}, error={}", task == null ? "?" : task.getTaskNo(), reason, e);
            }
            if (task != null) markFailed(task, reason);
            try {
                nackToDlq(channel, deliveryTag, task == null ? null : task, reason);
            } catch (IOException ignored) {
            }
        } finally {
            // 成功、失败、幂等跳过都要释放锁；token 校验保证不会误删其他实例的锁
            if (lockToken != null) {
                distributedLock.unlock(lockKey, lockToken);
            }
        }
    }

    /** Sentinel 熔断回调 */
    public String onBlocked(Message message, Channel channel, BlockException ex) {
        log.warn("OCR 消费者被 Sentinel 拦截: {}", ex.getRule());
        return "BLOCKED: " + ex.getRule();
    }

    // ==================== OCR 调用接口占位 ====================

    /**
     * 调用第三方 OCR 服务（本阶段只打日志，返回模拟识别文本）。
     *
     * 模块5接入真实服务时，替换这里的实现即可：
     * - 用 MinioUtil.getPresignedUrl(objectKey) 拿到图片临时访问地址；
     * - 调阿里云/腾讯云/百度 AI 的 OCR API；
     * - 解析返回的识别文本（按行列顺序拼接）。
     *
     * 返回值：整段识别文本（多行用 \n 拼接）。
     * 异常：OCR 服务调用失败抛 RuntimeException，上层 catch 后会进入死信队列。
     */
    private String callOcr(String paperImgObjectKey) {
        // 模块5 TODO：调用真实 OCR 服务
        // String imageUrl = minioUtil.getPresignedUrl(paperImgObjectKey);
        // HttpResponse resp = HttpRequest.post(ALIYUN_OCR_URL).header("Authorization", "Bearer " + API_KEY)
        //         .body(Map.of("url", imageUrl)).execute();
        // 解析 resp.body() 里的 text_list 字段拼接到一起返回

        log.warn("OCR 调用占位：模块5接入真实 OCR 服务，当前模拟返回空文本");
        // 模拟：返回一段占位文本，让后续 AI 阅卷能走通（真实识别结果替换这里即可）
        return "[OCR 占位] 这是模拟识别文本，模块5将接入真实 OCR 服务替换此实现。";
    }

    @Override
    protected String getTaskType() {
        return "OCR";
    }
}
