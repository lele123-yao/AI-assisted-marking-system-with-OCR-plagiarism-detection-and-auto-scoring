package com.marking.aimarkingsystem.agent.tool;

import cn.hutool.json.JSONObject;
import com.marking.aimarkingsystem.agent.mapper.HistoryCaseMapper;
import com.marking.aimarkingsystem.paper.entity.ExamQuestion;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 工具：历史同题型阅卷案例检索。
 *
 * 作用：给模型几份"同题型、已批改"的分数与评语作参照，减少凭空发挥。
 * 典型场景：模型不确定某类题该扣多少，参考历史案例的给分尺度。
 *
 * 隐私是硬约束，不是口头承诺：
 * - SQL 只 select 分数与评语四列，学生姓名、学号、原始作答根本不进入内存；
 * - 排除当前答卷，避免把"自己"当历史案例；
 * - 输出里只保留脱敏后的分数与评语，评语再截断，防止个案信息过多。
 *
 * 为什么不用向量检索做案例召回：
 * 历史案例是结构化数据（题型、分数），按题型精确过滤已经足够；
 * 混合检索留给非结构化的知识库文档，两类数据用不同的检索策略，边界更清晰。
 */
@Component
public class SearchHistoryCasesTool implements AgentTool {

    /** 最多返回案例数，太多会稀释参考价值并撑大上下文 */
    private static final int DEFAULT_LIMIT = 3;

    private static final int MAX_COMMENT_LENGTH = 200;

    private final HistoryCaseMapper historyCaseMapper;
    private final AnswerContextProvider contextProvider;

    public SearchHistoryCasesTool(HistoryCaseMapper historyCaseMapper,
                                  AnswerContextProvider contextProvider) {
        this.historyCaseMapper = historyCaseMapper;
        this.contextProvider = contextProvider;
    }

    @Override
    public String name() {
        return "search_history_cases";
    }

    @Override
    public String description() {
        return "检索同题型的历史批改案例，只返回脱敏后的分数与评语，用于校准给分尺度";
    }

    @Override
    public String argumentHint() {
        return "{\"questionType\": 数字（题型，可选）, \"answerId\": 数字（可选，用于排除本答卷）, "
                + "\"questionId\": 数字（可选，配合 answerId 推导题型）, \"limit\": 数字（可选，默认3）}";
    }

    @Override
    public String execute(JSONObject input) {
        Integer questionType = input.getInt("questionType");
        Long answerId = input.getLong("answerId");
        Long questionId = input.getLong("questionId");

        // 模型只给 answerId + questionId 时，自动从题目上取题型，少让模型记一个字段
        if (questionType == null && answerId != null && questionId != null) {
            ExamQuestion question = contextProvider.questionOf(answerId, questionId);
            questionType = question.getQuestionType();
        }
        if (questionType == null) {
            return "参数错误：请提供 questionType，或同时提供 answerId 与 questionId 以便推导题型。";
        }

        int limit = input.getInt("limit", DEFAULT_LIMIT);
        if (limit <= 0 || limit > 10) {
            limit = DEFAULT_LIMIT;
        }

        // 排除当前答卷；没有 answerId 时用 -1，保证 SQL 条件始终成立
        Long excludeAnswerId = answerId == null ? -1L : answerId;
        List<Map<String, Object>> cases = historyCaseMapper.selectCasesByQuestionType(
                questionType, excludeAnswerId, limit);
        if (cases.isEmpty()) {
            return "没有找到题型为 " + questionType + " 的历史批改案例。请主要依据本题评分细则判断。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【历史同题型案例】题型：").append(questionType)
                .append("；共 ").append(cases.size()).append(" 条（已脱敏，仅含分数与评语）\n");
        for (int i = 0; i < cases.size(); i++) {
            Map<String, Object> row = cases.get(i);
            sb.append("\n案例").append(i + 1)
                    .append("：AI 分 ").append(value(row.get("aiScore")))
                    .append("，教师定稿分 ").append(value(row.get("finalScore")))
                    .append("\n  AI 评语：").append(shorten(row.get("aiComment")))
                    .append("\n  教师评语：").append(shorten(row.get("finalComment"))).append("\n");
        }
        sb.append("\n说明：历史案例只是给分参考，不能直接套用分数；本题仍须按本题评分细则逐点判断。");
        return sb.toString();
    }

    private String value(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : "-";
    }

    private String shorten(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return "（空）";
        }
        return text.length() <= MAX_COMMENT_LENGTH
                ? text : text.substring(0, MAX_COMMENT_LENGTH) + "...(已截断)";
    }
}
