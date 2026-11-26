package com.marking.aimarkingsystem.common.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.marking.aimarkingsystem.common.util.DeepSeekLlmUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 限流 / 熔断配置。
 *
 * 背景：项目里原本只有 {@code @SentinelResource} 注解，但没有注册切面、也没有加载任何规则，
 * 注解实际不生效。本类补齐两件事：
 * 1. 注册 {@link SentinelResourceAspect}，让 {@code @SentinelResource} 真正被 AOP 拦截；
 * 2. 启动时用代码（FlowRuleManager / DegradeRuleManager）加载规则，不依赖 Sentinel Dashboard。
 *
 * 规则设计：
 * - aiMarkConsumer / ocrConsumer：QPS 限流，防止班级集中交卷时几百条消息瞬间打爆大模型 API 和 OCR 服务。
 *   超限时由消费者的 blockHandler 走 markFailed + 死信，交给定时补偿任务稍后重投，
 *   而不是把消息立刻 requeue 形成"热循环"。
 * - deepseekChat（大模型调用）：QPS 限流 + 异常比例熔断。下游连续失败（超时/5xx/返回脏数据）时快速失败，
 *   避免把线程都耗在等待外部 API 上；熔断窗口结束后自动半开探测恢复。
 *   被拦截或调用失败时统一返回 DeepSeekLlmUtil.DEGRADE_NOTICE 兜底文案，实现"降级不报错"。
 *
 * 所有阈值都可用 application.yml 的 sentinel.* 配置（或同名环境变量）覆盖。
 */
@Configuration
public class SentinelConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelConfig.class);

    /** 消费者资源名，与 @SentinelResource(value=...) 保持一致 */
    public static final String AI_MARK_RESOURCE = "aiMarkConsumer";
    public static final String OCR_RESOURCE = "ocrConsumer";

    /** 大模型调用资源名：直接复用 DeepSeekLlmUtil 上的常量，避免两处字面量不一致 */
    public static final String LLM_RESOURCE = DeepSeekLlmUtil.LLM_RESOURCE;

    /** AI 阅卷消费者每秒允许进入的条数 */
    @Value("${sentinel.ai-mark-qps:5}")
    private double aiMarkQps;

    /** OCR 消费者每秒允许进入的条数 */
    @Value("${sentinel.ocr-qps:5}")
    private double ocrQps;

    /** 大模型调用每秒允许的请求数 */
    @Value("${sentinel.llm-qps:10}")
    private double llmQps;

    /** 熔断：统计窗口内异常比例达到该值即熔断（0.5 = 50%） */
    @Value("${sentinel.llm-error-ratio:0.5}")
    private double llmErrorRatio;

    /** 熔断：统计窗口内最小请求数，样本太少时不触发熔断 */
    @Value("${sentinel.llm-min-request-amount:5}")
    private int llmMinRequestAmount;

    /** 熔断：统计窗口长度（毫秒） */
    @Value("${sentinel.llm-stat-interval-ms:10000}")
    private int llmStatIntervalMs;

    /** 熔断：熔断持续时长（秒），到期后半开探测 */
    @Value("${sentinel.llm-degrade-seconds:30}")
    private int llmDegradeSeconds;

    /**
     * 注册 Sentinel 注解切面。
     * 没有这个 Bean，@SentinelResource 只是一个普通注解，限流/熔断永远不会触发。
     */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    /** 容器启动完成后加载规则（规则加载不依赖 Web 容器，PostConstruct 足够） */
    @PostConstruct
    public void loadRules() {
        loadFlowRules();
        loadDegradeRules();
    }

    private void loadFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule aiMark = new FlowRule();
        aiMark.setResource(AI_MARK_RESOURCE);
        aiMark.setGrade(RuleConstant.FLOW_GRADE_QPS);
        aiMark.setCount(aiMarkQps);
        rules.add(aiMark);

        FlowRule ocr = new FlowRule();
        ocr.setResource(OCR_RESOURCE);
        ocr.setGrade(RuleConstant.FLOW_GRADE_QPS);
        ocr.setCount(ocrQps);
        rules.add(ocr);

        FlowRule llm = new FlowRule();
        llm.setResource(LLM_RESOURCE);
        llm.setGrade(RuleConstant.FLOW_GRADE_QPS);
        llm.setCount(llmQps);
        rules.add(llm);

        FlowRuleManager.loadRules(rules);
        log.info("Sentinel 限流规则已加载: {}={}/s, {}={}/s, {}={}/s",
                AI_MARK_RESOURCE, aiMarkQps, OCR_RESOURCE, ocrQps, LLM_RESOURCE, llmQps);
    }

    private void loadDegradeRules() {
        DegradeRule llm = new DegradeRule();
        llm.setResource(LLM_RESOURCE);
        // 异常比例熔断：窗口内请求数 >= minRequestAmount 且异常占比 >= count 时触发
        llm.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        llm.setCount(llmErrorRatio);
        llm.setTimeWindow(llmDegradeSeconds);
        llm.setMinRequestAmount(llmMinRequestAmount);
        llm.setStatIntervalMs(llmStatIntervalMs);

        List<DegradeRule> rules = new ArrayList<>();
        rules.add(llm);
        DegradeRuleManager.loadRules(rules);
        log.info("Sentinel 熔断规则已加载: {} 异常比例>{}, {}s 内请求>{} 时熔断 {}s",
                LLM_RESOURCE, llmErrorRatio, llmStatIntervalMs / 1000,
                llmMinRequestAmount, llmDegradeSeconds);
    }
}
