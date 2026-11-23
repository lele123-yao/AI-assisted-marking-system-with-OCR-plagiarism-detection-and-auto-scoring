package com.marking.aimarkingsystem.common.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis 滑动窗口限流工具（模块7）。
 *
 * 作用：限制某个行为在"最近 N 秒"内最多执行 M 次，例如：
 * - 登录接口：同一 IP 60 秒最多 5 次；
 * - 短信验证码：同一手机号 60 秒最多 1 次；
 * - AI 讲评生成：同一教师 10 分钟最多 3 次，避免大模型调用被打爆。
 *
 * 为什么用滑动窗口而不是固定窗口：
 * - 固定窗口（如"每分钟 0~59 秒计数"）在窗口切换时可能出现 2 倍流量突刺：
 *   第 59 秒 100 次 + 第 60 秒 100 次，实际 2 秒内 200 次；
 * - 滑动窗口始终只统计"当前时间往前 N 秒"，不会出现边界突刺，
 *   代价是需要用 ZSET 保存每次请求的时间戳，内存占用略高。
 *
 * 实现原理（Redis ZSET）：
 * - 每个 key 是一个 ZSET，score = 请求时间戳（毫秒），member = 时间戳 + 随机数；
 * - 每次请求先用 ZREMRANGEBYSCORE 删除窗口外的旧记录；
 * - 再用 ZCARD 统计窗口内请求数：小于 limit 才 ZADD 当前请求并放行；
 * - 整个过程用 Lua 脚本完成，Redis 单线程执行脚本，保证"删除-计数-写入"原子性，
 *   避免并发下先查后写导致的超限。
 *
 * 注意：
 * - 本类只提供工具方法，默认不拦截任何接口；是否限流、限多少由业务侧调用时决定；
 * - 方法参数非法（key 为空、limit <= 0）属于编程错误，直接抛 IllegalArgumentException；
 * - 生产环境建议给 key 统一加前缀（如 rate_limit:login:ip:1.2.3.4），便于运维排查。
 */
@Component
public class RedisSlidingWindowLimiter {

    /**
     * 滑动窗口 Lua 脚本。
     *
     * KEYS[1]：限流 key
     * ARGV[1]：当前时间戳（毫秒）
     * ARGV[2]：窗口长度（毫秒）
     * ARGV[3]：窗口内允许的最大请求数
     * ARGV[4]：本次请求的唯一 member
     *
     * 返回：1 放行，0 拒绝。
     */
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]

            -- 1. 删除窗口外的历史请求
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

            -- 2. 统计窗口内请求数
            local count = redis.call('ZCARD', key)
            if count < limit then
                -- 3. 未超限则记录本次请求，并刷新 key 过期时间
                redis.call('ZADD', key, now, member)
                redis.call('PEXPIRE', key, window)
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisSlidingWindowLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取一次执行许可。
     *
     * @param key           限流 key（业务侧拼好前缀与维度，如 rate_limit:login:ip:127.0.0.1）
     * @param limit         窗口内最大请求数
     * @param windowSeconds 窗口长度（秒）
     * @return true 放行；false 触发限流
     */
    public boolean tryAcquire(String key, int limit, long windowSeconds) {
        checkArguments(key, limit, windowSeconds);
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;
        // member 必须唯一，否则同一毫秒内的多次请求会被 ZSET 去重，导致计数偏小
        String member = now + "-" + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);

        Long result;
        try {
            result = redisTemplate.execute(ACQUIRE_SCRIPT, List.of(key),
                    String.valueOf(now),
                    String.valueOf(windowMillis),
                    String.valueOf(limit),
                    member);
        } catch (Exception e) {
            // Redis 不可用时降级放行，避免限流组件故障导致所有带 @RateLimit 的接口不可用
            org.slf4j.LoggerFactory.getLogger(RedisSlidingWindowLimiter.class)
                    .warn("Redis 限流执行失败，降级放行: key={}, err={}", key, e.getMessage());
            return true;
        }
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 查询当前窗口内已记录的请求数（只读诊断用，不计入本次请求）。
     */
    public long currentCount(String key, long windowSeconds) {
        checkArguments(key, 1, windowSeconds);
        long now = System.currentTimeMillis();
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, now - windowSeconds * 1000L);
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count == null ? 0L : count;
    }

    /**
     * 查询当前窗口内还能放行多少次（只读诊断用）。
     */
    public long remaining(String key, int limit, long windowSeconds) {
        checkArguments(key, limit, windowSeconds);
        return Math.max(0L, limit - currentCount(key, windowSeconds));
    }

    /**
     * 清空限流记录。
     * 典型场景：管理员解封某个被限流的 IP，或测试环境重置。
     */
    public void reset(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("限流 key 不能为空");
        }
        redisTemplate.delete(key);
    }

    private void checkArguments(String key, int limit, long windowSeconds) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("限流 key 不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("限流阈值必须大于 0");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("限流窗口必须大于 0 秒");
        }
    }
}
