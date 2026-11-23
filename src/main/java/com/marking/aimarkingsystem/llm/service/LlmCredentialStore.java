package com.marking.aimarkingsystem.llm.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 大模型 API Key 临时存储（BYOK：用户自带 Key）。
 *
 * 设计要点：
 * 1. **只存内存**：Key 属于敏感信息，不落库、不写文件、不打日志，后端重启即失效，
 *    用户重新在「大模型设置」页填一次即可，符合"临时保存"的定位。
 * 2. **按用户名隔离**：每个登录用户各自保存自己的 Key，互不覆盖。
 * 3. **带 TTL**：默认 12 小时过期，避免长期驻留内存。
 * 4. **严格按人隔离**：带了用户名就只用本人的 Key（没填则报错提示去配置），
 *    绝不占用他人额度；只有异步线程拿不到用户名（username 为 null）时，
 *    才回退到"最近一次保存且未过期"的 Key。
 */
@Component
public class LlmCredentialStore {

    /** 默认有效期：12 小时 */
    public static final long DEFAULT_TTL_MILLIS = 12 * 60 * 60 * 1000L;

    /** 一条用户凭据：apiUrl / model 为空表示使用 application.yml 中的默认值 */
    public record Credential(String apiKey, String apiUrl, String model, long expireAt) {
        public boolean expired(long now) {
            return now >= expireAt;
        }
    }

    private final ConcurrentMap<String, Credential> credentials = new ConcurrentHashMap<>();

    /** 最近一次保存过 Key 的用户名，供异步线程无上下文时回退取用 */
    private volatile String latestUsername;

    /** 保存（覆盖）指定用户的 Key */
    public void save(String username, String apiKey, String apiUrl, String model) {
        long expireAt = System.currentTimeMillis() + DEFAULT_TTL_MILLIS;
        credentials.put(username, new Credential(
                apiKey.trim(),
                blankToNull(apiUrl),
                blankToNull(model),
                expireAt));
        this.latestUsername = username;
    }

    /** 取指定用户未过期的 Key；不存在或已过期返回 null（过期顺便清理） */
    public Credential get(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        Credential credential = credentials.get(username);
        if (credential == null) {
            return null;
        }
        if (credential.expired(System.currentTimeMillis())) {
            credentials.remove(username);
            return null;
        }
        return credential;
    }

    /**
     * 解析用哪个 Key 调用大模型。
     *
     * - 有用户名（同步请求 / MQ 消息头带了触发人）：**只用自己的 Key**，
     *   没填就返回 null，由调用方提示"请先配置"，绝不占用他人额度；
     * - 用户名为 null（异步线程拿不到登录上下文）：回退到最近一次保存的 Key。
     */
    public Credential resolve(String username) {
        if (StringUtils.hasText(username)) {
            return get(username);
        }
        return getLatest();
    }

    /** 取最近一次保存且未过期的 Key；latest 失效时回退到任意一个未过期 Key */
    public Credential getLatest() {
        Credential latest = get(latestUsername);
        if (latest != null) {
            return latest;
        }
        for (String name : credentials.keySet()) {
            Credential other = get(name);
            if (other != null) {
                this.latestUsername = name;
                return other;
            }
        }
        return null;
    }

    /** 删除指定用户的 Key */
    public boolean remove(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return credentials.remove(username) != null;
    }

    /** Key 脱敏：保留头 3 位与尾 4 位，中间用 **** 代替，避免前端拿到完整 Key */
    public static String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String key = apiKey.trim();
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 3) + "****" + key.substring(key.length() - 4);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
