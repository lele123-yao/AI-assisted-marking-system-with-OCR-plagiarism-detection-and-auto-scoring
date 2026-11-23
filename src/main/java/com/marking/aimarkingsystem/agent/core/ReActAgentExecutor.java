package com.marking.aimarkingsystem.agent.core;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.marking.aimarkingsystem.agent.tool.AgentToolRegistry;
import com.marking.aimarkingsystem.common.util.DeepSeekLlmUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 手写 ReAct 执行器（不依赖 Spring AI）。
 *
 * 循环：Thought -> Action(工具调用) -> Observation -> 再思考，直到模型给出 finalAnswer。
 *
 * 为什么不用框架：
 * - 项目已有 DeepSeekLlmUtil，直接复用即可，不引入新的版本兼容风险；
 * - 循环、JSON 协议、最大轮次、异常兜底全部可见，面试时能讲清每一步；
 * - 以后要接别的模型，只改 DeepSeekLlmUtil，本类不动。
 *
 * 与模型约定的输出协议（严格 JSON，二选一）：
 * <pre>
 * {"thought":"...", "action":"工具名", "actionInput":{...}}
 * {"thought":"...", "finalAnswer":"最终回答"}          // 或对象，评分类 Agent 用对象
 * </pre>
 *
 * 四道刹车（防止模型死循环、烧 token）：
 * 1. 最大轮次 agent.max-steps（默认 4），到点强制收口；
 * 2. 连续两轮调用"同一工具 + 同一参数"直接终止，避免模型原地重试；
 * 3. Observation 回填前截断到 4000 字符，防止上下文被一份长答卷撑爆；
 * 4. 解析失败/没有 action 时按自然语言收口，保证循环一定能结束。
 *
 * 轨迹：传入 AgentTraceContext 时，每轮 Thought/Action/Observation 与最终结论都会落库；
 * 不传时（单测、脚本）照常运行，只是不持久化。
 */
@Component
public class ReActAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReActAgentExecutor.class);

    /** 观察结果回填到 prompt 时的最大长度，防止一次工具返回撑爆上下文 */
    private static final int MAX_OBSERVATION_LENGTH = 4000;

    private final DeepSeekLlmUtil llmUtil;

    @Value("${agent.max-steps:4}")
    private int maxSteps;

    public ReActAgentExecutor(DeepSeekLlmUtil llmUtil) {
        this.llmUtil = llmUtil;
    }

    /** 不带轨迹的运行，保留给不需要审计的调用方 */
    public AgentResult run(String username,
                           String systemPrompt,
                           String userPrompt,
                           AgentToolRegistry registry) {
        return run(username, systemPrompt, userPrompt, registry, null, 1);
    }

    /**
     * 执行一次 Agent 任务。
     *
     * @param username     当前登录用户名（BYOK：用本人 Key）
     * @param systemPrompt 角色 + 工具清单 + 输出协议
     * @param userPrompt   任务描述
     * @param registry     本次可用的工具集合
     * @param trace        轨迹上下文，可为 null；不为 null 时逐步落库
     * @param attempt      第几次尝试：1 为初评，2/3 为 Checker 打回后的修正
     */
    public AgentResult run(String username,
                           String systemPrompt,
                           String userPrompt,
                           AgentToolRegistry registry,
                           AgentTraceContext trace,
                           int attempt) {
        long start = System.currentTimeMillis();
        StringBuilder transcript = new StringBuilder(userPrompt);
        List<AgentStep> steps = new ArrayList<>();

        // 上一轮的"工具名 + 参数"指纹，用于识别原地重试
        String lastCallKey = null;

        for (int round = 1; round <= maxSteps; round++) {
            String raw = llmUtil.chatAs(username, systemPrompt, transcript.toString());
            JSONObject decision = parseDecision(raw);

            // 模型直接输出自然语言：按最终回答处理，接口不报错
            if (decision == null) {
                return finish(raw.trim(), steps, trace, attempt, start, true);
            }

            String finalAnswer = stringify(decision.get("finalAnswer"));
            if (StringUtils.hasText(finalAnswer)) {
                return finish(finalAnswer.trim(), steps, trace, attempt, start, true);
            }

            String toolName = decision.getStr("action");
            if (!StringUtils.hasText(toolName)) {
                // 没有 action 也没有 finalAnswer：把原文当回答，避免空转
                return finish(raw.trim(), steps, trace, attempt, start, true);
            }

            JSONObject input = toInput(decision.get("actionInput"));
            String callKey = toolName + "|" + input;

            // 连续两轮同一工具 + 同一参数：模型在原地打转，直接收口
            if (callKey.equals(lastCallKey)) {
                log.warn("Agent 连续两轮调用相同工具与参数，终止循环: tool={}, input={}", toolName, input);
                transcript.append("\n\n检测到你连续两轮调用同一工具且参数相同，工具循环已终止。")
                        .append("请立即基于已有信息输出 finalAnswer，不要再调用工具。");
                return forceFinal(username, systemPrompt, transcript, steps, trace, attempt, start, false);
            }
            lastCallKey = callKey;

            String thought = decision.getStr("thought", "");
            String observation = registry.execute(toolName, input);
            AgentStep step = new AgentStep(round, thought, toolName, input.toString(), observation);
            steps.add(step);
            if (trace != null) {
                trace.step(attempt, round, step);
            }
            log.info("Agent 第{}轮工具调用: tool={}, observation.len={}",
                    round, toolName, observation.length());

            transcript.append("\n\n[第").append(round).append("轮工具调用]\n")
                    .append("调用工具：").append(toolName)
                    .append("\n参数：").append(input)
                    .append("\nObservation：").append(truncate(observation, MAX_OBSERVATION_LENGTH))
                    .append("\n请继续：需要补充信息就继续调用工具（每次只调用一个），信息足够就输出 finalAnswer。");
        }

        // 达到最大轮次，强制收口
        transcript.append("\n\n已达到最大工具调用轮次，请直接输出 finalAnswer，不要再调用工具。");
        return forceFinal(username, systemPrompt, transcript, steps, trace, attempt, start, true);
    }

    // ==================== 收口 ====================

    /**
     * 强制收口：再调一次模型，只允许输出 finalAnswer。
     *
     * 即使模型仍然不听话，也把原文当答案返回，保证接口一定有结果。
     */
    private AgentResult forceFinal(String username, String systemPrompt, StringBuilder transcript,
                                   List<AgentStep> steps, AgentTraceContext trace, int attempt,
                                   long start, boolean success) {
        String raw = llmUtil.chatAs(username, systemPrompt, transcript.toString());
        JSONObject decision = parseDecision(raw);
        String answer = decision == null ? null : stringify(decision.get("finalAnswer"));
        if (!StringUtils.hasText(answer)) {
            answer = raw;
        }
        return finish(answer.trim(), steps, trace, attempt, start, success);
    }

    private AgentResult finish(String answer, List<AgentStep> steps, AgentTraceContext trace,
                               int attempt, long start, boolean success) {
        if (trace != null) {
            trace.finish(attempt, answer, success, System.currentTimeMillis() - start);
        }
        return new AgentResult(answer, steps);
    }

    // ==================== 解析与容错 ====================

    /** 从模型输出里截取 JSON；解析失败返回 null，交给调用方按自然语言处理 */
    private JSONObject parseDecision(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.replace("```json", "").replace("```", "").trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return JSONUtil.parseObj(text.substring(start, end + 1));
        } catch (Exception e) {
            log.debug("Agent 输出不是合法 JSON，按自然语言处理: {}", e.getMessage());
            return null;
        }
    }

    /** finalAnswer 支持字符串或对象：对象直接序列化，方便评分类 Agent 返回结构化结果 */
    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence text) {
            return text.toString();
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return JSONUtil.toJsonStr(value);
        }
        return String.valueOf(value);
    }

    /** actionInput 可能是对象，也可能是模型序列化后的字符串 */
    private JSONObject toInput(Object value) {
        if (value == null) {
            return new JSONObject();
        }
        if (value instanceof JSONObject json) {
            return json;
        }
        try {
            return JSONUtil.parseObj(value.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(已截断)";
    }
}
