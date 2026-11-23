package com.marking.aimarkingsystem.mark.consumer;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.answer.entity.ExamAnswer;
import com.marking.aimarkingsystem.answer.enums.AnswerSourceEnum;
import com.marking.aimarkingsystem.answer.enums.AnswerStatusEnum;
import com.marking.aimarkingsystem.answer.mapper.ExamAnswerMapper;
import com.marking.aimarkingsystem.answer.service.PaperAnswerItemService;
import com.marking.aimarkingsystem.common.util.DeepSeekLlmUtil;
import com.marking.aimarkingsystem.common.util.RedisDistributedLock;
import com.marking.aimarkingsystem.mark.entity.MarkTask;
import com.marking.aimarkingsystem.mark.mapper.MarkTaskMapper;
import com.marking.aimarkingsystem.paper.entity.ExamQuestion;
import com.marking.aimarkingsystem.paper.mapper.ExamPaperMapper;
import com.marking.aimarkingsystem.paper.mapper.ExamQuestionMapper;
import com.rabbitmq.client.Channel;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI 阅卷消费者（模块4-阶段4）。
 *
 * 业务流程：
 * 1. 幂等查 mark_task → 已是 status≥2 直接 ack；
 * 2. 查 exam_answer → 拿 paperId → 查该试卷全部题目；
 * 3. 逐题调用 DeepSeek.chat()：题干 + 学生答案 + 参考答案 + 评分细则 → 大模型给分+评语；
 * 4. 汇总各题得分 → aiReferenceScore；
 *    - ONLINE 模式：直接写 teacherFinalScore（AI 即终分，老师可改）+ status=3 待教师确认；
 *    - PAPER_SCAN 模式：只写 aiReferenceScore 参考分，teacherFinalScore 留给老师录，status 也置 3；
 * 5. mark_task status=2 成功 → basicAck；异常 → retry_count++ → 超过 MAX_RETRY 进死信。
 *
 * Sentinel 占位：@SentinelResource(value="aiMarkConsumer", blockHandler="onBlocked")
 * - Sentinel Dashboard 未启动时注解不生效，不影响运行；
 * - 生产启动 Dashboard 后可配置"每秒最多 5 次"限流 + "超过则直接失败"熔断，
 *   防止班级集中交卷把大模型 API 打爆。
 */
@Component
public class AiMarkConsumer extends MarkTaskConsumerSupport {

    private static final Logger log = LoggerFactory.getLogger(AiMarkConsumer.class);

    /** 大模型返回分数上限：题目满分 * 1.1 允许轻微超分（大模型偶尔会超），超过视作脏数据 */
    private static final BigDecimal MAX_SCORE_FACTOR = new BigDecimal("1.10");

    /** 分布式锁 Key 前缀：同一 taskNo 同一时刻只允许一个消费者处理 */
    private static final String LOCK_KEY_PREFIX = "lock:mark:ai:";

    /** 锁租约 10 分钟：AI 逐题调用大模型耗时较长，租约必须大于最坏处理时间 */
    private static final long LOCK_LEASE_MILLIS = 600_000L;

    private final ExamAnswerMapper examAnswerMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final ExamPaperMapper examPaperMapper;
    private final DeepSeekLlmUtil llmUtil;
    private final RedisDistributedLock distributedLock;
    private final PaperAnswerItemService paperAnswerItemService;

    public AiMarkConsumer(MarkTaskMapper markTaskMapper,
                          ExamAnswerMapper examAnswerMapper,
                          ExamQuestionMapper examQuestionMapper,
                          ExamPaperMapper examPaperMapper,
                          DeepSeekLlmUtil llmUtil,
                          RedisDistributedLock distributedLock,
                          PaperAnswerItemService paperAnswerItemService) {
        super(markTaskMapper);
        this.examAnswerMapper = examAnswerMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.examPaperMapper = examPaperMapper;
        this.llmUtil = llmUtil;
        this.distributedLock = distributedLock;
        this.paperAnswerItemService = paperAnswerItemService;
    }

    /**
     * 监听 AI 阅卷队列。
     * manual ack 由 yml 的 acknowledge-mode=manual 全局配置生效；
     * 这里不指定 containerFactory，走 Spring AMQP 默认容器工厂即可。
     */
    @RabbitListener(queues = "${mq.ai.queue:marking.ai.queue}")
    @SentinelResource(value = "aiMarkConsumer", blockHandler = "onBlocked")
    public void consume(String messageBody, Channel channel, Message message) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        MarkTask task = null;
        String lockKey = null;
        String lockToken = null;
        try {
            String taskNo = readTaskNo(message);
            // BYOK：消息头携带触发人用户名，用其自填的 Key 调用大模型；缺失时回退到最近一次保存的 Key
            String llmUser = readLlmUser(message);
            log.info("AI阅卷消费者收到消息: taskNo={}, llmUser={}", taskNo, llmUser);

            // ---- 分布式锁占位：同一 taskNo 同一时刻只允许一个实例处理 ----
            // 与 markProcessingIfIdle 的乐观状态更新互补：
            // 状态更新防"同一 DB 内并发"，分布式锁防"多实例重复消费"。
            // 获取失败说明另一个消费者正在处理，直接 ack 放弃本条重复消息，不重复调用大模型。
            lockKey = LOCK_KEY_PREFIX + taskNo;
            lockToken = distributedLock.tryLock(lockKey, LOCK_LEASE_MILLIS);
            if (lockToken == null) {
                log.warn("AI阅卷任务已被其他消费者持有锁，跳过重复消费: taskNo={}", taskNo);
                channel.basicAck(deliveryTag, false);
                return;
            }

            task = requireTask(taskNo);
            if (isTerminal(task)) {
                log.info("AI阅卷任务已是终态，幂等跳过: taskNo={}, status={}", taskNo, task.getStatus());
                ack(channel, deliveryTag, task);
                return;
            }
            if (!markProcessingIfIdle(task)) {
                log.warn("AI阅卷任务状态已被其他消费者推进，放弃处理: taskNo={}", taskNo);
                ack(channel, deliveryTag, task);
                return;
            }

            // ---- 查答卷 ----
            ExamAnswer answer = examAnswerMapper.selectById(task.getAnswerId());
            if (answer == null) {
                throw new IllegalStateException("答卷不存在: " + task.getAnswerId());
            }

            // ---- 逐题调用大模型 ----
            List<ExamQuestion> questions = examQuestionMapper.selectList(
                    Wrappers.<ExamQuestion>lambdaQuery()
                            .eq(ExamQuestion::getPaperId, answer.getPaperId())
                            .orderByAsc(ExamQuestion::getSortOrder));
            if (questions.isEmpty()) {
                throw new IllegalStateException("试卷没有题目: paperId=" + answer.getPaperId());
            }

            BigDecimal totalAiScore = BigDecimal.ZERO;
            JSONArray itemScores = new JSONArray();
            // 逐题结果同时收集为 Map，阅卷完成后写入 exam_answer_item 明细表
            List<Map<String, Object>> aiItems = new ArrayList<>();
            String answerContent = answer.getAnswerContent();
            String ocrText = answer.getOcrText();
            // 纸质扫描卷没有线上作答，逐题打分时把整段 OCR 文本交给大模型自行定位
            boolean paperScan = AnswerSourceEnum.PAPER_SCAN.name().equals(answer.getAnswerSource());

            for (ExamQuestion q : questions) {
                Map<String, Object> item = callLlmForQuestion(q, answerContent, ocrText, llmUser, paperScan);
                totalAiScore = totalAiScore.add((BigDecimal) item.get("score"));
                itemScores.add(item);
                aiItems.add(item);
            }
            totalAiScore = totalAiScore.setScale(2, RoundingMode.HALF_UP);

            // ---- 写答卷 ----
            ExamAnswer answerUpdate = new ExamAnswer();
            answerUpdate.setId(answer.getId());
            answerUpdate.setAiReferenceScore(totalAiScore);
            // 逐题明细 JSON，方便后续学情分析按题目聚合
            answerUpdate.setAnswerContent(itemScores.toString());
            answerUpdate.setMarkTime(java.time.LocalDateTime.now());

            if (Objects.equals(answer.getAnswerSource(), AnswerSourceEnum.ONLINE.name())) {
                // 线上答卷：AI 即终分，直接写 teacherFinalScore，状态推进到 3（待教师确认）
                answerUpdate.setTeacherFinalScore(totalAiScore);
                answerUpdate.setMarkingStatus(AnswerStatusEnum.PENDING_TEACHER_CONFIRM.getCode());
            } else if (Objects.equals(answer.getAnswerSource(), AnswerSourceEnum.PAPER_SCAN.name())) {
                // 纸质扫描：只写参考分，teacherFinalScore 留空等老师录分
                answerUpdate.setTeacherFinalScore(null);
                answerUpdate.setMarkingStatus(AnswerStatusEnum.PENDING_TEACHER_CONFIRM.getCode());
            } else {
                throw new IllegalStateException("未知答卷来源: " + answer.getAnswerSource());
            }
            examAnswerMapper.updateById(answerUpdate);
            // 逐题明细单独落表：只写 AI 两列，教师复核结果不会被覆盖
            paperAnswerItemService.saveAiItems(answer.getId(), aiItems);

            markSuccess(task);
            ack(channel, deliveryTag, task);
        } catch (Exception e) {
            String reason;
            if (e instanceof BlockException) {
                // Sentinel 限流/熔断
                reason = "Sentinel " + e.getClass().getSimpleName();
                log.warn("AI阅卷被 Sentinel 熔断: {}", reason);
            } else {
                reason = e.getMessage();
                log.error("AI阅卷消费异常: taskNo={}, error={}", task == null ? "?" : task.getTaskNo(), reason, e);
            }
            if (task != null) {
                markFailed(task, reason);
            }
            try {
                nackToDlq(channel, deliveryTag, task == null ? null : task, reason);
            } catch (IOException ignored) {
                // Channel 断了就不管了，mark_task 已回写 status=3/4，任务不会丢
            }
        } finally {
            // 无论成功、失败还是幂等跳过，都必须释放锁；token 不匹配时 unlock 不会误删他人锁
            if (lockToken != null) {
                distributedLock.unlock(lockKey, lockToken);
            }
        }
    }

    /** Sentinel 熔断回调：返回友好错误描述，不抛异常让框架吃掉 */
    public String onBlocked(String messageBody, Channel channel, Message message, BlockException ex) {
        log.warn("AI阅卷消费者被 Sentinel 拦截: {}", ex.getRule());
        return "BLOCKED: " + ex.getRule();
    }

    // ==================== 逐题调用大模型 ====================

    /**
     * 对单道题调用 DeepSeek，返回 {questionId, questionNo, score, comment}。
     *
     * Prompt 设计：
     * - System Prompt 给 AI 一个"高校阅卷老师"的角色定位；
     * - User Prompt 包含：题干 / 学生答案 / 参考答案 / 评分细则 / 满分；
     * - 要求 AI 严格输出 JSON，方便 parse；不遵守格式视作脏数据。
     *
     * 返回的 score 用题目满分 clamp（0 ~ score*1.1），防止大模型偶尔超分。
     */
    private Map<String, Object> callLlmForQuestion(ExamQuestion q, String onlineAnswerContent, String ocrText,
                                                   String llmUser, boolean paperScan) {
        String studentAnswer = paperScan ? ocrText : extractStudentAnswer(onlineAnswerContent, q.getId());
        // 纸质答卷没有线上作答内容，逐题打分时把整段 OCR 文本喂过去让 AI 自行定位对应答案
        // 线上答卷则按 answerContent（JSON 格式的逐题答案）取对应题目的作答

        String systemPrompt = "你是一名经验丰富的高校阅卷老师，请严格按评分细则为考生答案打分，并说明每一分的依据。"
                + "你必须且只能返回一段 JSON，格式为 "
                + "{\"score\": 数字, \"comment\": \"评分依据\"}，其中 score 是你给的得分（小数一位即可）。"
                + "comment 必须包含三部分：①命中的得分点及对应分值；②缺失或错误导致的扣分点；"
                + "③一句改进建议。总长度 50~200 字。"
                + "不要返回 JSON 以外的任何文字、不要用 markdown 包裹。";

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("【题目】题号：").append(q.getQuestionNo()).append("\n");
        if (q.getSectionName() != null) {
            userPrompt.append("题型：").append(q.getSectionName()).append("\n");
        }
        userPrompt.append("题干：").append(nullToEmpty(q.getQuestionContent())).append("\n");
        if (q.getStandardAnswer() != null) {
            userPrompt.append("【参考答案】").append(q.getStandardAnswer()).append("\n");
        }
        if (q.getScoringRule() != null) {
            userPrompt.append("【评分细则】").append(q.getScoringRule()).append("\n");
        }
        userPrompt.append("【满分】").append(q.getScore()).append("分\n");
        userPrompt.append("【考生答案】").append(nullToEmpty(studentAnswer)).append("\n");
        if (paperScan) {
            userPrompt.append("说明：考生答案是整份试卷的 OCR 文本，请自行定位本题对应的作答；")
                    .append("如果找不到明确作答，按 0 分处理并在评语中说明。\n");
        }
        userPrompt.append("请逐条对照评分细则，写清楚每个得分点是否命中、扣分原因，得分不能超过满分。仅返回 JSON。");

        // BYOK：优先用触发阅卷的用户自己的 Key；llmUser 为空或未配置时回退到最近一次保存的 Key
        String raw = llmUtil.chatAs(llmUser, systemPrompt, userPrompt.toString());
        JSONObject parsed = parseLlmJson(raw);

        double rawScore = parsed.getDouble("score");
        BigDecimal score = BigDecimal.valueOf(rawScore)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxAllowed = q.getScore().multiply(MAX_SCORE_FACTOR);
        if (score.compareTo(BigDecimal.ZERO) < 0) score = BigDecimal.ZERO;
        if (score.compareTo(maxAllowed) > 0) {
            log.warn("AI 给分超上限，clamp: questionNo={}, rawScore={}, maxAllowed={}",
                    q.getQuestionNo(), rawScore, maxAllowed);
            score = q.getScore();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("questionId", q.getId());
        result.put("questionNo", q.getQuestionNo());
        result.put("score", score);
        result.put("comment", parsed.getStr("comment", ""));
        return result;
    }

    /** 从 DeepSeek 输出里提取 JSON 对象（大模型可能在 JSON 前后加废话） */
    private JSONObject parseLlmJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("大模型返回为空");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            throw new RuntimeException("大模型返回非JSON: " + raw.substring(0, Math.min(200, raw.length())));
        }
        return JSONUtil.parseObj(raw.substring(start, end + 1));
    }

    /**
     * 从线上答卷的 answerContent（JSON 格式的逐题答案）里提取指定题目的作答。
     * 线上答卷 answerContent 是 [{questionId, answer}, ...] 数组。
     */
    private String extractStudentAnswer(String answerContent, Long questionId) {
        if (!JSONUtil.isTypeJSON(answerContent)) {
            return nullToEmpty(answerContent);
        }
        JSONArray array = JSONUtil.parseArray(answerContent);
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (Objects.equals(item.getLong("questionId"), questionId)) {
                return item.getStr("answer");
            }
        }
        return null;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 读取消息头里的触发人用户名（BYOK）；不存在返回 null，由 chatAs 回退到最近一次保存的 Key */
    private String readLlmUser(Message message) {
        Object value = message.getMessageProperties().getHeader("llmUser");
        return value == null ? null : value.toString();
    }

    @Override
    protected String getTaskType() {
        return "AI_MARK";
    }
}
