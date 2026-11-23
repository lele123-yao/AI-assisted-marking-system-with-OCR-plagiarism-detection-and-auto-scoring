package com.marking.aimarkingsystem.agent.core;

import java.util.List;

/**
 * Agent 执行结果：最终回答 + 工具调用轨迹。
 */
public record AgentResult(String answer, List<AgentStep> steps) {
}
