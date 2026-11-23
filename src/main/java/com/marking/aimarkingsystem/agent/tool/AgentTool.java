package com.marking.aimarkingsystem.agent.tool;

import cn.hutool.json.JSONObject;

import java.util.List;

/**
 * Agent 可调用工具的统一接口。
 *
 * 每个实现都注册为 Spring Bean，由 AgentToolRegistry 自动收集；
 * 新增工具只需实现本接口并加 @Component，不需要改执行器。
 */
public interface AgentTool {

    /** 工具名，必须是英文小写下划线，模型用它发起调用 */
    String name();

    /** 工具用途，写进系统提示词，决定模型什么时候选它 */
    String description();

    /** 参数示例，写进系统提示词，降低模型传错参数的概率 */
    String argumentHint();

    /**
     * 必填参数名。
     *
     * 注册中心会在调用工具前统一校验，缺参/类型不对直接返回可读错误文本，
     * 不再依赖每个工具自己写 if (xxx == null) 判断，也避免模型漏参导致空指针。
     * 默认无必填参数，只需要 name/description/argumentHint 的旧工具不用改。
     */
    default List<String> requiredArgs() {
        return List.of();
    }

    /**
     * 执行工具。
     *
     * @param input 模型给出的参数，可能为空
     * @return 给模型看的观察结果（文本），不要抛异常，失败也返回可读文本
     */
    String execute(JSONObject input);
}
