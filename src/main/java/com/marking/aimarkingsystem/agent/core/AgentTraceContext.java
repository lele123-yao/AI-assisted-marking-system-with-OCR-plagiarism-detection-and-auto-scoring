package com.marking.aimarkingsystem.agent.core;

import com.marking.aimarkingsystem.agent.service.AgentTraceService;

/**
 * 一次 Agent 运行的轨迹上下文。
 *
 * 执行器只依赖这个轻量对象，不直接依赖数据库；
 * 没有传上下文时（例如单元测试）执行器照常工作，只是不落库。
 */
public record AgentTraceContext(String runNo,
                                String agentType,
                                Long answerId,
                                Long questionId,
                                AgentTraceService traceService) {

    /** Checker 轨迹行的轮次标记：99 表示"不是工具调用，是自检" */
    public static final int CHECKER_ROUND = 99;

    public void step(int attempt, int round, AgentStep step) {
        traceService.recordStep(runNo, agentType, answerId, questionId, attempt, round, step);
    }

    /**
     * 记录一次 Checker 自检。
     *
     * 复用同一张轨迹表：前端按 roundNo=99 就能把"自检"从普通工具调用里区分出来，
     * 不需要为 Checker 单独建表。
     */
    public void checker(int attempt, String input, String observation, boolean passed) {
        AgentStep step = new AgentStep(CHECKER_ROUND, passed ? "自检通过" : "自检未通过",
                "checker", input, observation);
        traceService.recordStep(runNo, agentType, answerId, questionId, attempt, CHECKER_ROUND, step);
    }

    public void finish(int attempt, String finalAnswer, boolean success, long durationMs) {
        traceService.recordFinish(runNo, agentType, answerId, questionId, attempt,
                finalAnswer, success, durationMs);
    }
}
