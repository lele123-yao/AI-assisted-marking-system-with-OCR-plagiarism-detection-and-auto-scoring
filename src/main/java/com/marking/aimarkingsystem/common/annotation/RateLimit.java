package com.marking.aimarkingsystem.common.annotation;

import com.marking.aimarkingsystem.common.enums.RateLimitType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解（模块7）。
 *
 * 标注在 Controller 方法上，由 {@code RateLimitAspect} 基于 Redis 滑动窗口
 * （{@code RedisSlidingWindowLimiter}）判断是否放行；超限直接返回 429。
 *
 * 示例：
 * <pre>
 * &#64;RateLimit(key = "answer:submit", limit = 5, windowSeconds = 60)
 * &#64;PostMapping("/online")
 * public Result&lt;Long&gt; submitOnline(...) { ... }
 * </pre>
 *
 * 设计说明：
 * - key 只填业务标识，最终 Redis Key 由切面拼接为
 *   {@code rate_limit:{key}:{user|ip|global}}，避免业务侧手写 Key 造成混乱；
 * - 同一接口不同用户的计数相互独立（type=USER 时按用户名区分），
 *   不会出现"一个用户频繁调用导致全班被限流"；
 * - 限流是本接口的最后一道保护，不和业务逻辑耦合：业务代码无需感知限流存在。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 业务标识，建议"模块:动作"，如 answer:submit；为空时使用"类名.方法名" */
    String key() default "";

    /** 窗口内允许的最大请求次数 */
    int limit() default 20;

    /** 窗口长度（秒），统计范围是"当前时间往前 windowSeconds 秒" */
    int windowSeconds() default 60;

    /** 限流维度：按用户 / 按 IP / 全局限流 */
    RateLimitType type() default RateLimitType.USER;

    /** 超限时的提示语 */
    String message() default "操作过于频繁，请稍后再试";
}
