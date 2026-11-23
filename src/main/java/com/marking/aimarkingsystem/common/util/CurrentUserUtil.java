package com.marking.aimarkingsystem.common.util;

import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.ResultCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具类。
 *
 * 身份来源只有 SecurityContextHolder（由 JwtAuthenticationFilter 在请求进入时写入），
 * 不做额外 DB 查询。用户名即 sys_user.username。
 *
 * 使用场景：
 * - BYOK（用户自带大模型 Key）：按用户名存取各自的 Key；
 * - 异步线程（MQ 消费者）没有登录上下文，调用 {@link #usernameOrNull()} 会返回 null，
 *   调用方需自行降级，不要抛异常。
 */
public final class CurrentUserUtil {

    private CurrentUserUtil() {
    }

    /**
     * 返回当前登录用户名；未登录 / 匿名返回 null（不抛异常，供异步线程安全调用）。
     */
    public static String usernameOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        String name = authentication.getName();
        return "anonymousUser".equals(name) ? null : name;
    }

    /**
     * 返回当前登录用户名；未登录直接抛 401 业务异常。
     */
    public static String requireUsername() {
        String username = usernameOrNull();
        if (username == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return username;
    }
}
