package com.marking.aimarkingsystem.common.util;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.marking.aimarkingsystem.llm.service.LlmCredentialStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * DeepSeek 大模型调用工具类（模块4：AI 阅卷 / OCR 后处理）。
 *
 * 封装 HTTP 调用细节，让业务侧只管拼 prompt，不关心鉴权头、请求体格式、响应解析。
 * 选用 Hutool HttpRequest 而不是 RestTemplate，原因：
 * - 项目已引入 hutool-all，无额外依赖；
 * - 同步接口、链式 API 更适合"单次打分"这种短调用场景；
 * - 不引入 OkHttp / Apache HttpClient 等额外传递依赖。
 *
 * 异常处理策略：
 * - 网络超时 / DNS 失败：HttpRequest 抛 RuntimeException，捕获后包装为 BusinessException 上层决定重试；
 * - HTTP 非 2xx：读 body 里的 error.message（DeepSeek 规范格式），拼成可读异常抛出；
 * - 响应体格式异常（缺 choices[0].message.content）：视为大模型返回了脏数据，抛异常让业务侧走降级；
 * - 所有异常都在 warn 级别打日志，业务侧只需要关心"拿到结果 or 抛了异常"。
 *
 * 配置项全部走 yml（llm.*），不在代码里硬编码 api-key。
 * 生产环境务必通过环境变量覆盖 ${LLM_API_KEY}，不要把真实密钥提交到仓库。
 */
@Slf4j
@Component
public class DeepSeekLlmUtil {

    /** 大模型调用在 Sentinel 中的资源名：SentinelConfig 按此名加载限流与熔断规则 */
    public static final String LLM_RESOURCE = "deepseekChat";

    /**
     * 限流/熔断降级时返回的兜底文案。
     *
     * 为什么返回文案而不是抛异常：
     * 熔断期间继续往上抛异常，会把同一个"下游不可用"信号放大成各业务自己的失败处理，
     * 调用方无法区分"模型答错了"和"模型根本没被调用"。返回统一文案后，
     * 各业务可以据此判断是否降级（如 AI 阅卷延后重试、讲评报告提示稍后再试）。
     */
    public static final String DEGRADE_NOTICE =
            "【AI 服务暂时不可用】大模型接口已限流或熔断，本次请求已降级处理，请稍后重试或改人工处理。";

    /** 连接超时（毫秒）：大模型 API 首字节较慢，设 30s */
    private static final int CONNECT_TIMEOUT_MS = 30_000;

    /** 读取超时（毫秒）：流式关闭后整包返回，设 120s 足够 */
    private static final int READ_TIMEOUT_MS = 120_000;

    @Value("${llm.provider:deepseek}")
    private String provider;

    /** API 基地址，默认 DeepSeek 官方 endpoint */
    @Value("${llm.api-url:https://api.deepseek.com/chat/completions}")
    private String apiUrl;

    /** 模型名，默认 deepseek-chat；业务侧也可传参覆盖 */
    @Value("${llm.model:deepseek-chat}")
    private String defaultModel;

    /** API Key，**生产环境用环境变量覆盖**，示例：spring:el: 或 -Dllm.api-key=${LLM_API_KEY} */
    @Value("${llm.api-key:}")
    private String apiKey;

    /** 用户自带 Key 的临时存储（BYOK）。用户未填时回退到上面的全局 apiKey */
    private final LlmCredentialStore credentialStore;

    public DeepSeekLlmUtil(LlmCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    /**
     * 发送单次 chat 请求，返回大模型的文本回复。
     *
     * 最简重载：只有 userPrompt，没有系统提示词；适合简单查询场景。
     *
     * @param userPrompt 用户 prompt（必填）
     * @return 大模型返回的 content；失败抛 RuntimeException
     */
    public String chat(String userPrompt) {
        return chat(defaultModel, null, userPrompt);
    }

    /**
     * 发送单次 chat 请求，返回大模型的文本回复。
     *
     * AI 阅卷的典型用法：
     * <pre>
     * String result = deepSeekLlmUtil.chat("你是一名高数阅卷老师",
     *     "题目：xxx\n学生答案：xxx\n参考答案：xxx\n请给出评分和评语");
     * </pre>
     *
     * @param systemPrompt 系统提示词（可空：空字符串或 null 视为不传 system 消息）
     * @param userPrompt   用户 prompt（必填）
     * @return 大模型返回的 content 字符串；失败抛 RuntimeException
     */
    public String chat(String systemPrompt, String userPrompt) {
        return chat(defaultModel, systemPrompt, userPrompt);
    }

    /**
     * 完整参数重载：允许覆盖模型名（后续若接入多模型路由，业务侧可指定）。
     *
     * @param model        模型名（可空：空字符串或 null 用配置默认值）
     * @param systemPrompt 系统提示词（可空）
     * @param userPrompt   用户 prompt（必填）
     * @return 大模型返回的 content 字符串
     */
    public String chat(String model, String systemPrompt, String userPrompt) {
        return doChat(apiKey, apiUrl, model, systemPrompt, userPrompt);
    }

    /**
     * BYOK 调用：使用指定登录用户自己填写的 Key。
     *
     * 适合同步调用（如学情讲评，请求线程内有登录上下文）。
     * 规则：传了用户名就只用该用户自己的 Key（没填则抛异常提示去设置页填写），
     * 不会占用他人额度；username 为 null（异步线程无上下文）时才回退到最近一次保存的 Key。
     *
     * @param username     登录用户名（可为 null：异步线程无上下文时直接用最近一次保存的 Key）
     * @param systemPrompt 系统提示词（可空）
     * @param userPrompt   用户 prompt（必填）
     */
    @SentinelResource(value = LLM_RESOURCE, blockHandler = "onBlocked", fallback = "onDegrade")
    public String chatAs(String username, String systemPrompt, String userPrompt) {
        return chatAs(username, null, systemPrompt, userPrompt);
    }

    /**
     * BYOK 调用（可覆盖模型名）。
     *
     * @param username     登录用户名（可为 null）
     * @param model        模型名（可空：优先用用户保存的，其次用 yml 默认值）
     * @param systemPrompt 系统提示词（可空）
     * @param userPrompt   用户 prompt（必填）
     */
    @SentinelResource(value = LLM_RESOURCE, blockHandler = "onBlocked", fallback = "onDegrade")
    public String chatAs(String username, String model, String systemPrompt, String userPrompt) {
        LlmCredentialStore.Credential credential = credentialStore.resolve(username);
        if (credential == null) {
            throw new IllegalStateException(
                    "尚未配置大模型 API Key，请先在「大模型设置」页面填写你自己的 API Key");
        }
        String resolvedApiUrl = StringUtils.hasText(credential.apiUrl()) ? credential.apiUrl() : apiUrl;
        String resolvedModel = StringUtils.hasText(model)
                ? model
                : (StringUtils.hasText(credential.model()) ? credential.model() : defaultModel);
        return doChat(credential.apiKey(), resolvedApiUrl, resolvedModel, systemPrompt, userPrompt);
    }

    /**
     * BYOK 调用（可覆盖模型名与采样温度）。
     *
     * temperature 来自评分提示词模板：评分场景建议 0~0.3；为 null 时不传，使用服务商默认值。
     *
     * @param username     登录用户名（可为 null）
     * @param model        模型名（可空：优先用用户保存的，其次用 yml 默认值）
     * @param systemPrompt 系统提示词（可空）
     * @param userPrompt   用户 prompt（必填）
     * @param temperature  采样温度（可空）
     */
    @SentinelResource(value = LLM_RESOURCE, blockHandler = "onBlocked", fallback = "onDegrade")
    public String chatAs(String username, String model, String systemPrompt, String userPrompt,
                         BigDecimal temperature) {
        LlmCredentialStore.Credential credential = credentialStore.resolve(username);
        if (credential == null) {
            throw new IllegalStateException(
                    "尚未配置大模型 API Key，请先在「大模型设置」页面填写你自己的 API Key");
        }
        String resolvedApiUrl = StringUtils.hasText(credential.apiUrl()) ? credential.apiUrl() : apiUrl;
        String resolvedModel = StringUtils.hasText(model)
                ? model
                : (StringUtils.hasText(credential.model()) ? credential.model() : defaultModel);
        return doChat(credential.apiKey(), resolvedApiUrl, resolvedModel, systemPrompt, userPrompt, temperature);
    }

    /**
     * 真实发请求的核心实现：显式传入 Key / 地址 / 模型，便于全局配置与 BYOK 两种来源复用。
     */
    private String doChat(String key, String url, String model, String systemPrompt, String userPrompt) {
        return doChat(key, url, model, systemPrompt, userPrompt, null);
    }

    private String doChat(String key, String url, String model, String systemPrompt, String userPrompt,
                          BigDecimal temperature) {
        if (!StringUtils.hasText(userPrompt)) {
            throw new IllegalArgumentException("userPrompt 不能为空");
        }
        if (!StringUtils.hasText(key)) {
            // api-key 没配直接失败，比等到调用时报 401 更早暴露配置问题
            throw new IllegalStateException("llm.api-key 未配置，请在 application.yml、环境变量或「大模型设置」页面中设置");
        }
        String resolvedModel = StringUtils.hasText(model) ? model : defaultModel;
        String resolvedSystem = StringUtils.hasText(systemPrompt) ? systemPrompt : null;

        String requestBody = buildRequestBody(resolvedModel, resolvedSystem, userPrompt, temperature);
        log.debug("调用 LLM: provider={}, model={}, systemPrompt.len={}, userPrompt.len={}",
                provider, resolvedModel,
                resolvedSystem == null ? 0 : resolvedSystem.length(),
                userPrompt.length());

        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .timeout(CONNECT_TIMEOUT_MS)
                .execute()) {
            String body = response.body();
            if (!response.isOk()) {
                String errorMsg = extractErrorMessage(body);
                log.error("LLM 调用失败: status={}, body={}", response.getStatus(), body);
                throw new RuntimeException("LLM 调用失败[" + response.getStatus() + "]: " + errorMsg);
            }
            String content = extractContent(body);
            if (!StringUtils.hasText(content)) {
                log.warn("LLM 返回了空 content，body={}", body);
                throw new RuntimeException("LLM 返回内容为空");
            }
            return content;
        } catch (RuntimeException e) {
            // 把 HttpRequest 抛出的网络异常也统一转为 RuntimeException，保持调用方 catch 一处即可
            log.error("LLM 调用异常: provider={}, model={}, error={}", provider, resolvedModel, e.getMessage());
            throw new RuntimeException("LLM 调用异常: " + e.getMessage(), e);
        }
    }

    // ==================== Sentinel 限流/熔断降级回调 ====================

    /**
     * 判断一段返回值是否是降级兜底文案。
     * 调用方据此区分"模型真实输出"和"本次根本没调到模型"，避免把兜底文案当成评分/答案使用。
     */
    public static boolean isDegradeNotice(String text) {
        return DEGRADE_NOTICE.equals(text);
    }

    /*
     * blockHandler：被限流或熔断拦截（BlockException）时走这里。
     * 与业务方法同签名（原参数 + BlockException），返回值类型一致（String）。
     */
    public String onBlocked(String username, String systemPrompt, String userPrompt,
                            BlockException ex) {
        return degrade(ex);
    }

    public String onBlocked(String username, String model, String systemPrompt, String userPrompt,
                            BlockException ex) {
        return degrade(ex);
    }

    public String onBlocked(String username, String model, String systemPrompt, String userPrompt,
                            BigDecimal temperature, BlockException ex) {
        return degrade(ex);
    }

    /*
     * fallback：调用本身失败（超时、5xx、返回脏数据）时走这里。
     * 只有这里的异常被兜住，Sentinel 的异常比例熔断才能真正累计到失败率。
     */
    public String onDegrade(String username, String systemPrompt, String userPrompt, Throwable ex) {
        return degrade(ex);
    }

    public String onDegrade(String username, String model, String systemPrompt, String userPrompt,
                            Throwable ex) {
        return degrade(ex);
    }

    public String onDegrade(String username, String model, String systemPrompt, String userPrompt,
                            BigDecimal temperature, Throwable ex) {
        return degrade(ex);
    }

    /** 统一记录降级原因并返回兜底文案（不抛异常，保证调用方拿到可展示的结果） */
    private String degrade(Throwable ex) {
        if (ex instanceof BlockException) {
            log.warn("大模型调用被 Sentinel 拦截，返回降级文案: rule={}", ((BlockException) ex).getRule());
        } else {
            log.warn("大模型调用失败，返回降级文案: {}", ex == null ? "unknown" : ex.getMessage());
        }
        return DEGRADE_NOTICE;
    }

    // ==================== 内部方法 ====================

    /**
     * 构建 DeepSeek Chat Completions 请求体。
     * stream=false；temperature 有值时才传，评分场景由模板控制（建议 0~0.3）。
     */
    private String buildRequestBody(String model, String systemPrompt, String userPrompt,
                                    BigDecimal temperature) {
        JSONArray messages = new JSONArray();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new JSONObject(Map.of("role", "system", "content", systemPrompt)));
        }
        messages.add(new JSONObject(Map.of("role", "user", "content", userPrompt)));

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        // stream=false：一次性返回整包，方便直接解析
        body.put("stream", false);
        if (temperature != null) {
            body.put("temperature", temperature);
        }

        return JSONUtil.toJsonStr(body);
    }

    /**
     * 从成功响应体中提取 choices[0].message.content。
     * 格式：{"choices":[{"message":{"content":"xxx",...},...}],...}
     */
    private String extractContent(String body) {
        try {
            JSONObject json = JSONUtil.parseObj(body);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JSONObject first = choices.getJSONObject(0);
            JSONObject message = first.getJSONObject("message");
            if (message == null) {
                return null;
            }
            return message.getStr("content");
        } catch (Exception e) {
            log.warn("解析 LLM 响应体失败: body={}, error={}", body, e.getMessage());
            return null;
        }
    }

    /**
     * 从失败响应体中提取 error.message（DeepSeek 错误规范格式）。
     * 格式：{"error":{"message":"xxx","type":"invalid_request_error",...}}
     * 解析失败时返回原始 body 兜底，不让错误信息丢失。
     */
    private String extractErrorMessage(String body) {
        try {
            JSONObject json = JSONUtil.parseObj(body);
            JSONObject error = json.getJSONObject("error");
            if (error != null) {
                return error.getStr("message", body);
            }
        } catch (Exception ignored) {
            // 解析失败就用原始 body，不影响错误上报
        }
        return body;
    }
}
