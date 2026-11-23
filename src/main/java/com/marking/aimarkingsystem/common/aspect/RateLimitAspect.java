package com.marking.aimarkingsystem.common.aspect;

import com.marking.aimarkingsystem.common.annotation.RateLimit;
import com.marking.aimarkingsystem.common.enums.RateLimitType;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.common.util.RedisSlidingWindowLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 接口限流切面（模块7）。
 *
 * 执行链路：
 * 1. 拦截标注 {@code @RateLimit} 的方法；
 * 2. 按注解的 type 生成维度后缀：USER → 用户名（未登录退化 IP）、IP → 客户端 IP、GLOBAL → fixed；
 * 3. 拼接 Redis Key：{@code rate_limit:{业务key}:{维度值}}；
 * 4. 调用 {@link RedisSlidingWindowLimiter#tryAcquire} 做滑动窗口判断；
 * 5. 超限抛 BusinessException（code=429），由全局异常处理器统一返回 Result，业务方法不执行。
 *
 * 与操作日志切面的顺序：
 * - OperationLogAspect 是 HIGHEST_PRECEDENCE（最外层），本切面是 HIGHEST_PRECEDENCE + 10；
 *   因此"被限流拒绝的请求"也会被操作日志记录，便于分析是谁在刷接口。
 *
 * 为什么限流放在权限校验之前：
 * - 未登录请求同样会消耗应用资源，先限流可以挡掉恶意刷接口；
 * - 限流只依赖 SecurityContext（由 JWT 过滤器提前写入），不依赖权限校验结果。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    /** 与 yml 中 rate-limit.key-prefix 保持一致 */
    private static final String KEY_PREFIX = "rate_limit:";

    private final RedisSlidingWindowLimiter limiter;

    public RateLimitAspect(RedisSlidingWindowLimiter limiter) {
        this.limiter = limiter;
    }

    // 用全限定名绑定注解，避免依赖编译参数名（-parameters），详见 OperationLogAspect 注释。
    @Around("@annotation(com.marking.aimarkingsystem.common.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        RateLimit rateLimit = ((MethodSignature) joinPoint.getSignature())
                .getMethod().getAnnotation(RateLimit.class);
        String businessKey = StringUtils.hasText(rateLimit.key())
                ? rateLimit.key()
                : joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        String redisKey = KEY_PREFIX + businessKey + ":" + resolveDimension(rateLimit.type());

        boolean allowed = limiter.tryAcquire(redisKey, rateLimit.limit(), rateLimit.windowSeconds());
        if (!allowed) {
            log.warn("接口限流拦截: key={}, limit={}, window={}s, user={}",
                    redisKey, rateLimit.limit(), rateLimit.windowSeconds(), currentUserName());
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, rateLimit.message());
        }

        return joinPoint.proceed();
    }

    // ==================== 限流维度 ====================

    private String resolveDimension(RateLimitType type) {
        return switch (type) {
            case USER -> {
                String username = currentUserName();
                yield username != null ? "user:" + username : "ip:" + clientIp();
            }
            case IP -> "ip:" + clientIp();
            case GLOBAL -> "global";
        };
    }

    /** 取当前登录用户名；未登录返回 null */
    private String currentUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                int comma = forwarded.indexOf(',');
                return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return realIp.trim();
            }
            return request.getRemoteAddr();
        }
        return "unknown";
    }
}
