package com.marking.aimarkingsystem.agent.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.marking.aimarkingsystem.agent.checker.CheckResult;
import com.marking.aimarkingsystem.agent.checker.CheckerAgent;
import com.marking.aimarkingsystem.agent.core.AgentResult;
import com.marking.aimarkingsystem.agent.core.AgentStep;
import com.marking.aimarkingsystem.agent.core.AgentTraceContext;
import com.marking.aimarkingsystem.agent.core.ReActAgentExecutor;
import com.marking.aimarkingsystem.agent.core.ScoreDraft;
import com.marking.aimarkingsystem.agent.tool.AnswerContextProvider;
import com.marking.aimarkingsystem.agent.tool.AgentToolRegistry;
import com.marking.aimarkingsystem.agent.vo.AgentReviewVO;
import com.marking.aimarkingsystem.agent.vo.AgentStepVO;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.paper.entity.ExamQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 阅卷 Agent（方案 A）：主推理 Agent + Checker 自检 Agent。
 *
 * 两层的分工：
 * - 主 Agent：手写 ReAct 循环，自主决定查题目、查细则、查知识库、查历史案例，给出初评；
 * - Checker：纯规则校验，不调工具、不打分，只判断"分数是否合法、依据是否矛盾、理由是否来自细则"。
 *
 * 为什么要拆两层：
 * 单层 ReAct 的问题是"自己评自己"，模型写完评语就直接返回，没人检查它是否算错。
 * Checker 相当于阅卷组长的复核：发现矛盾打回重评，最多 2 轮；
 * 2 轮仍不通过时保留原始初评并标记 degraded，保证接口一定能返回，不阻断教师操作。
 *
 * 与现有异步阅卷的区别：
 * - AiMarkConsumer 是"单次调用、批量跑完整卷"，追求吞吐，适合自动出参考分；
 * - 本 Agent 是"教师手动触发的单题深度复核"，追求可解释性，用来解释分数或发现偏差。
 *
 * 硬边界：本类属于 Agent 包，不写任何业务表（只读答卷/题目/知识库），
 * 也不会被批量阅卷主链路调用。
 */
@Service
public class MarkingReviewAgentService {

    private static final Logger log = LoggerFactory.getLogger(MarkingReviewAgentService.class);

    /** 主 Agent 的输出协议，两处提示词共用，避免格式漂移 */
    private static final String SCORE_JSON_HINT =
            "{\"score\": 数字, \"comment\": \"评分依据：①命中得分点及分值；②扣分点及分值；③改进建议\"}";

    /** 最多允许 Checker 打回修正 2 轮；仍失败则保留原始初评 */
    private static final int MAX_CORRECTION_ROUNDS = 2;

    /** 轨迹里的 Agent 类型 */
    private static final String AGENT_TYPE = "MARKING_REVIEW";

    private final ReActAgentExecutor executor;
    private final AgentToolRegistry toolRegistry;
    private final AnswerContextProvider contextProvider;
    private final AgentTraceService traceService;
    private final CheckerAgent checkerAgent;

    public MarkingReviewAgentService(ReActAgentExecutor executor,
                                     AgentToolRegistry toolRegistry,
                                     AnswerContextProvider contextProvider,
                                     AgentTraceService traceService,
                                     CheckerAgent checkerAgent) {
        this.executor = executor;
        this.toolRegistry = toolRegistry;
        this.contextProvider = contextProvider;
        this.traceService = traceService;
        this.checkerAgent = checkerAgent;
    }

    /**
     * 复核单题。
     *
     * 流程：主 Agent 初评 -> Checker 校验 -> 不通过则带着问题回主 Agent 重评（最多 2 轮）
     * -> 通过则采用修正结果，仍不通过则保留原始初评并标记降级。
     */
    public AgentReviewVO review(Long answerId, Long questionId, String username) {
        ExamQuestion question = contextProvider.questionOf(answerId, questionId);
        BigDecimal fullScore = question.getScore() == null
                ? new BigDecimal("100") : question.getScore();
        String scoringRule = question.getScoringRule();

        String runNo = traceService.newRunNo("AR");
        AgentTraceContext trace = new AgentTraceContext(
                runNo, AGENT_TYPE, answerId, questionId, traceService);

        // ===== 第一层：主 Agent 初评 =====
        AgentResult firstRun = runMainAgent(username, answerId, questionId, question,
                fullScore, trace, 1, null);
        ScoreDraft draft = parseScore(firstRun.answer());
        if (draft == null) {
            throw new BusinessException("阅卷 Agent 未返回可解析的评分结果："
                    + shorten(firstRun.answer(), 200));
        }
        draft = clamp(draft, fullScore);

        List<AgentStep> allSteps = new ArrayList<>(firstRun.steps());

        // ===== 第二层：Checker 自检（纯规则，不调工具） =====
        CheckResult check = checkerAgent.check(draft, fullScore, scoringRule);
        recordChecker(trace, 1, draft, check);
        log.info("阅卷 Agent 初评完成: runNo={}, score={}, checkerPassed={}",
                runNo, draft.score(), check.passed());

        ScoreDraft result = draft;
        int correctionRounds = 0;

        if (!check.passed()) {
            ScoreDraft current = draft;
            for (int attempt = 2; attempt <= 1 + MAX_CORRECTION_ROUNDS; attempt++) {
                correctionRounds = attempt - 1;
                try {
                    // 把 Checker 的意见原样作为新一轮任务输入，模型重新调工具核验
                    AgentResult correction = runMainAgent(username, answerId, questionId, question,
                            fullScore, trace, attempt, buildCorrectionPrompt(current, check));
                    ScoreDraft candidate = parseScore(correction.answer());
                    if (candidate == null) {
                        log.warn("第{}轮修正未返回可解析结果: runNo={}", correctionRounds, runNo);
                        continue;
                    }
                    candidate = clamp(candidate, fullScore);
                    allSteps.addAll(correction.steps());

                    check = checkerAgent.check(candidate, fullScore, scoringRule);
                    recordChecker(trace, attempt, candidate, check);
                    current = candidate;
                    log.info("阅卷 Agent 第{}轮修正: runNo={}, score={}, checkerPassed={}",
                            correctionRounds, runNo, candidate.score(), check.passed());
                    if (check.passed()) {
                        result = candidate;
                        break;
                    }
                } catch (Exception e) {
                    // 修正轮失败（网络抖动、Key 失效等）不能影响已经拿到的初评
                    log.warn("阅卷 Agent 第{}轮修正失败，准备降级: runNo={}, error={}",
                            correctionRounds, runNo, e.getMessage());
                    break;
                }
            }
        }

        // 修正失败：明确回到原始初评，Checker 无权覆盖主 Agent 的初评
        boolean degraded = !check.passed() && correctionRounds > 0;
        if (!check.passed()) {
            result = draft;
        }
        boolean corrected = check.passed() && correctionRounds > 0;

        AgentReviewVO vo = new AgentReviewVO();
        vo.setRunNo(runNo);
        vo.setQuestionId(question.getId());
        vo.setQuestionNo(question.getQuestionNo());
        vo.setFullScore(fullScore);
        vo.setDraftScore(draft.score());
        vo.setDraftComment(draft.comment());
        vo.setFinalScore(result.score());
        vo.setFinalComment(result.comment());
        vo.setCheckerPassed(check.passed());
        vo.setCheckerIssues(check.issues());
        vo.setCorrected(corrected);
        vo.setCorrectionRounds(correctionRounds);
        vo.setDegraded(degraded);
        vo.setSteps(allSteps.stream().map(AgentStepVO::from).toList());
        return vo;
    }

    // ==================== 主 Agent 调用 ====================

    /**
     * 跑一次主 Agent。
     *
     * @param attempt        第几次尝试：1 初评，2/3 为修正
     * @param correctionNote 修正时 Checker 的反馈；初评为 null
     */
    private AgentResult runMainAgent(String username, Long answerId, Long questionId,
                                     ExamQuestion question, BigDecimal fullScore,
                                     AgentTraceContext trace, int attempt, String correctionNote) {
        String systemPrompt = buildSystemPrompt(question, fullScore);
        String userPrompt = buildUserPrompt(answerId, questionId, question, correctionNote);
        try {
            return executor.run(username, systemPrompt, userPrompt, toolRegistry, trace, attempt);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("阅卷 Agent 调用失败：" + e.getMessage());
        }
    }

    // ==================== 提示词 ====================

    private String buildSystemPrompt(ExamQuestion question, BigDecimal fullScore) {
        return "你是一名高校阅卷复核 Agent，负责核验第 " + question.getQuestionNo()
                + " 题（满分 " + plain(fullScore) + " 分）的评分。\n"
                + "可用工具：\n" + toolRegistry.describe() + "\n"
                + "【强制约束】\n"
                + "1. 只能依据工具返回的题干、参考答案、评分细则、考生作答和知识库内容作答，"
                + "严禁使用你自己的记忆或常识补充评分点；\n"
                + "2. 第一步必须调用 get_question_detail（answerId 与 questionId 已在下文给出）；\n"
                + "3. 建议继续用 extract_student_answer 清洗作答、用 score_point_stats 统计得分点、"
                + "用 check_score_consistency 核对算术、用 search_history_cases 校准尺度、"
                + "用 search_knowledge 找评分规范；不需要的工具不要硬调；\n"
                + "4. 评语必须逐条对应评分细则：命中哪些得分点（各几分）、扣了哪些点（各几分），"
                + "评语里写的分必须与总分算得上；\n"
                + "5. 如果工具查不到题目或评分细则，禁止编造得分点，直接输出 "
                + "{\"score\": 0, \"comment\": \"资料不足，无法判定：<缺少什么资料>\"}；\n"
                + "6. 如果学生确实未作答，可以给 0 分，并在评语中写明“未作答”，这不属于资料不足；\n"
                + "7. 每次只调用一个工具，信息足够后立即收口；得分必须在 0 到满分之间。\n"
                + "输出协议（严格 JSON，不要 markdown）：\n"
                + "{\"thought\":\"简短思考\",\"action\":\"工具名\",\"actionInput\":{...}}\n"
                + "或\n"
                + "{\"thought\":\"核对完成\",\"finalAnswer\":" + SCORE_JSON_HINT + "}";
    }

    private String buildUserPrompt(Long answerId, Long questionId, ExamQuestion question,
                                   String correctionNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("请复核答卷 ").append(answerId).append(" 中 questionId=").append(questionId)
                .append(" 的这道题（第 ").append(question.getQuestionNo()).append(" 题）。\n")
                .append("先调用 get_question_detail 获取真实数据，再按评分细则给出分数和依据。");
        if (StringUtils.hasText(correctionNote)) {
            sb.append("\n\n").append(correctionNote);
        }
        return sb.toString();
    }

    /** 把 Checker 的问题拼成修正指令，要求模型重新核验而不是凭空改数字 */
    private String buildCorrectionPrompt(ScoreDraft previous, CheckResult check) {
        return "【Checker 自检未通过】\n"
                + "上一版评分：" + plain(previous.score()) + " 分\n"
                + "上一版依据：" + previous.comment() + "\n"
                + check.toFeedback()
                + "\n请重新核验并修正：必要时重新调用工具核对评分细则与学生作答，"
                + "保证分数、评语、细则三者一致。仍按同一 JSON 协议输出。";
    }

    // ==================== 轨迹 ====================

    private void recordChecker(AgentTraceContext trace, int attempt, ScoreDraft draft, CheckResult check) {
        String input = "分数=" + plain(draft.score()) + "；评语=" + shorten(draft.comment(), 300);
        trace.checker(attempt, input, check.toFeedback(), check.passed());
    }

    // ==================== 解析 ====================

    private ScoreDraft parseScore(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String cleaned = text.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JSONObject json = JSONUtil.parseObj(cleaned.substring(start, end + 1));
            Double score = json.getDouble("score");
            if (score == null) {
                return null;
            }
            return new ScoreDraft(
                    BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP),
                    json.getStr("comment", ""));
        } catch (Exception e) {
            return null;
        }
    }

    /** 数值兜底：无论模型返回什么，最终分数一定落在 [0, 满分] */
    private ScoreDraft clamp(ScoreDraft draft, BigDecimal fullScore) {
        BigDecimal score = draft.score();
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            score = BigDecimal.ZERO;
        }
        if (score.compareTo(fullScore) > 0) {
            score = fullScore;
        }
        return new ScoreDraft(score.setScale(2, RoundingMode.HALF_UP), draft.comment());
    }

    private String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private String shorten(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
