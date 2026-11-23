package com.marking.aimarkingsystem.agent.tool;

import cn.hutool.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具：查看某份答卷的总览（学生、试卷、逐题分值）。
 * 教师助手 Agent 进入对话时先调它建立上下文。
 */
@Component
public class GetAnswerOverviewTool implements AgentTool {

    private final AnswerContextProvider contextProvider;

    public GetAnswerOverviewTool(AnswerContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    @Override
    public String name() {
        return "get_answer_overview";
    }

    @Override
    public String description() {
        return "查询一份答卷的基本信息和逐题得分概况，用于回答整体性问题";
    }

    @Override
    public String argumentHint() {
        return "{\"answerId\": 数字}";
    }

    @Override
    public List<String> requiredArgs() {
        return List.of("answerId");
    }

    @Override
    public String execute(JSONObject input) {
        Long answerId = input.getLong("answerId");
        if (answerId == null) {
            return "参数错误：answerId 必填";
        }
        return contextProvider.overview(answerId);
    }
}
