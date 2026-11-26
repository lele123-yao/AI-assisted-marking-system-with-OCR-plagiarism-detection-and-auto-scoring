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
import com.marking.aimarkingsystem.paper.util.OcrClient;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

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
 * Sentinel：@SentinelResource(value="ocrConsumer", blockHandler="onBlocked")，规则由 SentinelConfig 加载。
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
    private final OcrClient ocrClient;

    public OcrConsumer(MarkTaskMapper markTaskMapper,
                       ExamAnswerMapper examAnswerMapper,
                       MinioUtil minioUtil,
                       MarkTaskProducerService markTaskProducerService,
                       PlagiarismService plagiarismService,
                       RedisDistributedLock distributedLock,
                       OcrClient ocrClient) {
        super(markTaskMapper);
        this.examAnswerMapper = examAnswerMapper;
        this.minioUtil = minioUtil;
        this.markTaskProducerService = markTaskProducerService;
        this.plagiarismService = plagiarismService;
        this.distributedLock = distributedLock;
        this.ocrClient = ocrClient;
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

    /**
     * Sentinel 限流/熔断回调。
     *
     * 返回值必须与 consume 一致（void），否则 Sentinel 反射校验失败；
     * 回写任务失败 + nack 进死信，由定时补偿任务稍后重投，避免消息 requeue 热循环。
     */
    public void onBlocked(Message message, Channel channel, BlockException ex) {
        log.warn("OCR 消费者被 Sentinel 拦截: {}", ex.getRule());
        handleBlocked(message, channel, "Sentinel 限流/熔断: " + ex.getClass().getSimpleName());
    }

    // ==================== OCR 调用接口占位 ====================

    /**
     * 调用配置的 OCR 服务识别整份答卷图片。
     *
     * - 用 MinioUtil.download(objectKey) 读出图片字节；
     * - 通过 OcrClient 调用配置的 OCR HTTP 服务（paper-parse.ocr-url）；
     * - 未配置时直接失败重试，不再返回占位文本，避免假数据进入 AI 阅卷。
     *
     * 返回值：整段识别文本（多行用 \n 拼接）。
     * 异常：OCR 服务调用失败抛 RuntimeException，上层 catch 后会进入死信队列。
     */
    private String callOcr(String paperImgObjectKey) {
        if (!ocrClient.isConfigured()) {
            throw new IllegalStateException("未配置 OCR 服务（paper-parse.ocr-url），无法识别答卷扫描件");
        }
        try (InputStream in = minioUtil.download(paperImgObjectKey)) {
            String text = ocrClient.recognize(in.readAllBytes(), "answer.jpg");
            log.info("答卷 OCR 识别完成: objectKey={}, textLength={}", paperImgObjectKey, text.length());
            return text;
        } catch (IOException e) {
            throw new IllegalStateException("读取答卷图片失败: " + e.getMessage(), e);
        }
    }

    @Override
    protected String getTaskType() {
        return "OCR";
    }
}
