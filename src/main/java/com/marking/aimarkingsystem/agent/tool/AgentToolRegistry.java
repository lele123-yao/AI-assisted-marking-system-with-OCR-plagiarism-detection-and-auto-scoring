package com.marking.aimarkingsystem.agent.tool;

import cn.hutool.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 工具注册表：收集容器里所有 AgentTool，并提供"给模型看的说明书"与安全执行入口。
 *
 * 三层防护（对应面试里的"模型乱传参/乱调工具怎么兜"）：
 * 1. 白名单：只有注册过的工具能执行，未注册直接返回"工具不存在"，不会反射调用任意方法；
 * 2. 参数校验：按各工具声明的 requiredArgs 校验必填项与数字格式，错误信息回给模型自行修正；
 * 3. 超时熔断：工具在独立线程池执行，超过 agent.tool-timeout-ms 直接返回失败文本，
 *    既不抛异常打断 ReAct 循环，也不会让一个卡住的 HTTP/数据库把整个请求拖死。
 *
 * 设计取舍：
 * - 工具按 name 去重，重名直接启动失败，避免模型调用到不确定的实现；
 * - 只暴露 name/description/argumentHint，不把工具内部实现细节写进 prompt。
 */
@Component
public class AgentToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentToolRegistry.class);

    /**
     * 工具默认超时。
     *
     * 默认 8000ms 而不是 3000ms：知识库工具内部可能走 embedding HTTP，
     * 3 秒会把向量召回误判成超时，导致混合检索永远降级。
     * 纯数据库工具远低于这个值，不会因此变慢。
     */
    @Value("${agent.tool-timeout-ms:8000}")
    private long toolTimeoutMs;

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    /**
     * 工具专用线程池。
     *
     * 不用 ForkJoin 公共池：工具调用是阻塞 IO（数据库、embedding HTTP），
     * 公共池并行度等于 CPU 核数，几个并发的 Agent 请求就能把它占满。
     * daemon 线程不阻止 JVM 退出，这里不需要额外的 shutdown 钩子。
     */
    private final ExecutorService toolPool = Executors.newFixedThreadPool(8, runnable -> {
        Thread thread = new Thread(runnable, "agent-tool");
        thread.setDaemon(true);
        return thread;
    });

    public AgentToolRegistry(List<AgentTool> toolList) {
        for (AgentTool tool : toolList) {
            AgentTool previous = tools.put(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException("Agent 工具重名: " + tool.name());
            }
        }
    }

    /** 生成系统提示词里的工具清单 */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (AgentTool tool : tools.values()) {
            sb.append("- ").append(tool.name()).append("：").append(tool.description())
                    .append("；参数：").append(tool.argumentHint()).append("\n");
        }
        return sb.toString();
    }

    public boolean contains(String name) {
        return StringUtils.hasText(name) && tools.containsKey(name);
    }

    /**
     * 执行工具；未知工具、参数错误、执行超时、内部异常都转成文本返回，
     * 让模型拿到 Observation 后自己决定重试、换工具还是收口。
     */
    public String execute(String name, JSONObject input) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return "工具不存在：" + name + "。可用工具：" + tools.keySet()
                    + "。请从可用工具中重新选择，不要编造工具名。";
        }

        JSONObject args = input == null ? new JSONObject() : input;
        String argumentError = validate(tool, args);
        if (argumentError != null) {
            return argumentError;
        }

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> tool.execute(args), toolPool);
        try {
            String result = future.get(toolTimeoutMs, TimeUnit.MILLISECONDS);
            return result == null ? "工具返回为空" : result;
        } catch (TimeoutException e) {
            // 取消只是标记中断，底层阻塞调用未必立刻停下，但调用方不再等待
            future.cancel(true);
            log.warn("Agent 工具执行超时: tool={}, timeout={}ms", name, toolTimeoutMs);
            return "工具调用超时（超过 " + toolTimeoutMs + "ms）：" + name
                    + "。请基于已有信息作答，或换一个工具。";
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("Agent 工具执行失败: tool={}, error={}", name, cause.getMessage());
            return "工具执行失败：" + cause.getMessage();
        }
    }

    /**
     * 结构化校验：必填项不能缺失，以 Id 结尾的参数必须是数字。
     *
     * 只做"能提前发现"的校验，业务合法性仍由工具自己判断（例如 answerId 是否存在），
     * 这样注册中心不需要知道任何业务语义。
     */
    private String validate(AgentTool tool, JSONObject args) {
        for (String field : tool.requiredArgs()) {
            Object value = args.get(field);
            if (value == null || (value instanceof CharSequence text && !StringUtils.hasText(text))) {
                return "参数错误：缺少必填参数 " + field + "。正确格式：" + tool.argumentHint();
            }
            if (field.endsWith("Id") && !isNumber(value)) {
                return "参数错误：" + field + " 必须是数字，当前值：" + value
                        + "。正确格式：" + tool.argumentHint();
            }
        }
        return null;
    }

    private boolean isNumber(Object value) {
        try {
            Long.parseLong(String.valueOf(value).trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
