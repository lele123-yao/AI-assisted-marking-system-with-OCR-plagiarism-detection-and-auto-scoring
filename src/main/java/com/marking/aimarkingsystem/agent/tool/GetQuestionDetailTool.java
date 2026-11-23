package com.marking.aimarkingsystem.agent.tool;

import cn.hutool.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具：查看某道题的完整信息（题干、参考答案、评分细则、考生作答、当前评分）。
 * 阅卷复核 Agent 必须先用它拿数据，教师助手在追问具体题目时也会用到。
 */
@Component
public class GetQuestionDetailTool implements AgentTool {

    private final AnswerContextProvider contextProvider;

    public GetQuestionDetailTool(AnswerContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    @Override
    public String name() {
        return "get_question_detail";
    }

    @Override
    public String description() {
        return "查询答卷中某道题的题干、参考答案、评分细则、考生作答和当前评分";
    }

    @Override
    public String argumentHint() {
        return "{\"answerId\": 数字, \"questionId\": 数字}";
    }

    @Override
    public List<String> requiredArgs() {
        return List.of("answerId", "questionId");
    }

    @Override
    public String execute(JSONObject input) {
        Long answerId = input.getLong("answerId");
        Long questionId = input.getLong("questionId");
        if (answerId == null || questionId == null) {
            return "参数错误：answerId 与 questionId 均必填";
        }
        return contextProvider.questionDetail(answerId, questionId);
    }
}
