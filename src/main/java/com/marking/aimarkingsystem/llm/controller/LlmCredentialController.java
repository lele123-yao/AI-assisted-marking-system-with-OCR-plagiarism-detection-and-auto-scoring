package com.marking.aimarkingsystem.llm.controller;

import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.common.util.CurrentUserUtil;
import com.marking.aimarkingsystem.common.util.DeepSeekLlmUtil;
import com.marking.aimarkingsystem.llm.dto.LlmCredentialRequest;
import com.marking.aimarkingsystem.llm.service.LlmCredentialStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 大模型 Key 配置接口（BYOK：用户自带 Key）。
 *
 * 安全约定：
 * - Key 只存后端内存（LlmCredentialStore），不落库、不写文件；
 * - 查询接口只返回脱敏后的 Key（头 3 位 + 尾 4 位），绝不回传完整 Key；
 * - 日志只记录用户名，不记录 Key。
 *
 * 默认全部接口要求已登录（SecurityConfig 未放行），每个用户只能操作自己的 Key。
 */
@Slf4j
@RestController
@RequestMapping("/llm/credential")
public class LlmCredentialController {

    private final LlmCredentialStore credentialStore;
    private final DeepSeekLlmUtil llmUtil;

    public LlmCredentialController(LlmCredentialStore credentialStore, DeepSeekLlmUtil llmUtil) {
        this.credentialStore = credentialStore;
        this.llmUtil = llmUtil;
    }

    /** 查询当前用户是否已配置 Key（只返回脱敏信息） */
    @GetMapping
    public Result<Map<String, Object>> status() {
        String username = CurrentUserUtil.requireUsername();
        return Result.success(toStatus(username));
    }

    /** 保存 / 覆盖当前用户的 Key */
    @PostMapping
    public Result<Map<String, Object>> save(@RequestBody LlmCredentialRequest request) {
        String username = CurrentUserUtil.requireUsername();

        String apiKey = request.getApiKey() == null ? "" : request.getApiKey().trim();
        if (apiKey.length() < 8) {
            throw new BusinessException("API Key 格式不正确，请粘贴完整的 Key");
        }
        String apiUrl = request.getApiUrl() == null ? null : request.getApiUrl().trim();
        if (apiUrl != null && !apiUrl.isEmpty()) {
            String lower = apiUrl.toLowerCase();
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                throw new BusinessException("服务地址必须以 http:// 或 https:// 开头");
            }
        }

        credentialStore.save(username, apiKey, apiUrl, request.getModel());
        log.info("用户已保存大模型 Key（仅存内存）: user={}", username);
        return Result.success("已保存，后续 AI 调用将使用你的 Key", toStatus(username));
    }

    /** 清除当前用户的 Key */
    @DeleteMapping
    public Result<Void> remove() {
        String username = CurrentUserUtil.requireUsername();
        credentialStore.remove(username);
        log.info("用户已清除大模型 Key: user={}", username);
        return Result.success();
    }

    /** 测试当前 Key 是否可用（会真实发起一次极短调用，消耗用户自己的少量 token） */
    @PostMapping("/test")
    public Result<String> test() {
        String username = CurrentUserUtil.requireUsername();
        if (credentialStore.get(username) == null) {
            throw new BusinessException("请先保存 API Key，再测试连接");
        }
        try {
            String reply = llmUtil.chatAs(username,
                    "你是连通性测试助手，请只回复两个字：可用",
                    "请回复：可用");
            return Result.success("连接成功，模型回复：" + abbreviate(reply, 60));
        } catch (Exception e) {
            log.warn("大模型连接测试失败: user={}, error={}", username, e.getMessage());
            throw new BusinessException("连接失败：" + abbreviate(e.getMessage(), 200));
        }
    }

    // ==================== 内部方法 ====================

    private Map<String, Object> toStatus(String username) {
        LlmCredentialStore.Credential credential = credentialStore.get(username);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", credential != null);
        status.put("maskedKey", credential == null ? "" : LlmCredentialStore.mask(credential.apiKey()));
        status.put("apiUrl", credential == null || credential.apiUrl() == null ? "" : credential.apiUrl());
        status.put("model", credential == null || credential.model() == null ? "" : credential.model());
        status.put("expireAt", credential == null ? null : credential.expireAt());
        return status;
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
