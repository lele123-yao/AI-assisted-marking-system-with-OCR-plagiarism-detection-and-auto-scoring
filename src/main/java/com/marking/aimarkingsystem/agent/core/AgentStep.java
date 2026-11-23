package com.marking.aimarkingsystem.agent.core;

/**
 * 一次 ReAct 工具调用的轨迹，返回给前端展示"Agent 是怎么想的、查了什么"。
 */
public record AgentStep(int step, String thought, String tool, String input, String observation) {
}
