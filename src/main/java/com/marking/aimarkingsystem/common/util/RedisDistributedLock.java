package com.marking.aimarkingsystem.common.util;

import com.marking.aimarkingsystem.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 分布式锁工具（模块7）。
 *
 * 作用：在分布式部署（多台应用实例）下，保证同一业务操作同一时刻只有一个线程执行，例如：
 * - 同一学生的答卷只允许提交一次；
 * - 同一试卷的 AI 阅卷任务避免重复触发；
 * - 成绩归档、报告生成等幂等操作。
 *
 * 实现要点：
 * 1. 加锁：SET key token NX PX leaseMillis（Redis 2.6.12+ 的原子写法）
 *    - NX：key 不存在才写入，保证互斥；
 *    - PX：设置租约，防止进程崩溃后锁永远不释放（死锁）；
 *    - token：每次加锁生成的唯一值，释放时用于确认"锁还是自己的"。
 * 2. 解锁：用 Lua 先 GET 比较 token，再 DEL。
 *    - 不能直接 DEL：如果业务执行时间超过租约，锁已过期并被其他线程获取，
 *      直接 DEL 会误删别人的锁。
 * 3. 等待：获取失败时按固定间隔重试，直到 waitMillis 超时，避免线程无限自旋。
 *
 * 注意：
 * - 本实现不可重入：同一线程重复加同一个 key 会失败；需要重入时请改用 Redisson；
 * - 锁的 token 由调用方保存并在 finally 中释放，推荐直接用 executeWithLock；
 * - 租约应大于业务最大执行时间；若业务可能超时，需要额外的"看门狗"续期，
 *   本阶段不引入（毕设场景用 executeWithLock + 合理租约即可）。
 */
@Slf4j
@Component
public class RedisDistributedLock {

    /** 默认重试间隔（毫秒） */
    private static final long RETRY_INTERVAL_MILLIS = 50L;

    /**
     * 释放锁 Lua：只有 value 与加锁时的 token 一致才删除。
     * 返回 1 表示释放成功，0 表示锁已不属于当前持有者（可能已过期被他人获取）。
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取锁，最多等待 waitMillis 毫秒。
     *
     * @param key         锁 key，建议带业务前缀，如 lock:answer:submit:101
     * @param waitMillis  最长等待时间；0 表示只尝试一次
     * @param leaseMillis 锁租约，业务执行超过该时间锁会自动失效
     * @return 锁凭证 token；获取失败返回 null
     */
    public String tryLock(String key, long waitMillis, long leaseMillis) {
        checkArguments(key, leaseMillis);

        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + Math.max(waitMillis, 0L);

        while (true) {
            Boolean acquired;
            try {
                acquired = redisTemplate.opsForValue()
                        .setIfAbsent(key, token, leaseMillis, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // Redis 不可用时降级为非分布式执行：单实例部署下 DB 乐观状态更新仍能防重复消费
                log.warn("Redis 不可用，分布式锁降级放行: key={}, err={}", key, e.getMessage());
                return token;
            }
            if (Boolean.TRUE.equals(acquired)) {
                return token;
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return null;
            }
            try {
                Thread.sleep(Math.min(RETRY_INTERVAL_MILLIS, remaining));
            } catch (InterruptedException e) {
                // 恢复中断标记，交由上层决定是否终止
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    /**
     * 立即尝试获取锁（不等待）。
     *
     * @return 锁凭证 token；获取失败返回 null
     */
    public String tryLock(String key, long leaseMillis) {
        return tryLock(key, 0L, leaseMillis);
    }

    /**
     * 释放锁。
     *
     * @return true 释放成功；false 锁不存在或已不属于当前 token
     */
    public boolean unlock(String key, String token) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(token)) {
            return false;
        }
        try {
            Long result = redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
            return Long.valueOf(1L).equals(result);
        } catch (Exception e) {
            // 降级放行模式下没有真实的 Redis 锁可释放，忽略即可
            log.warn("Redis 不可用，释放分布式锁跳过: key={}, err={}", key, e.getMessage());
            return false;
        }
    }

    /** 判断锁是否被占用（仅诊断用，不能代替加锁） */
    public boolean isLocked(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 模板方法：自动加锁、执行、释放锁。
     *
     * 示例：
     * <pre>
     * lock.executeWithLock("lock:answer:submit:" + answerId, 1000, 30000, () -> {
     *     answerService.submit(answerId);
     *     return true;
     * });
     * </pre>
     *
     * @throws BusinessException 获取锁失败时抛出，提示稍后重试
     */
    public <T> T executeWithLock(String key, long waitMillis, long leaseMillis, Supplier<T> action) {
        Objects.requireNonNull(action, "锁内执行逻辑不能为空");
        String token = tryLock(key, waitMillis, leaseMillis);
        if (token == null) {
            throw new BusinessException("系统繁忙，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            unlock(key, token);
        }
    }

    private void checkArguments(String key, long leaseMillis) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("锁 key 不能为空");
        }
        if (leaseMillis <= 0) {
            throw new IllegalArgumentException("锁租约必须大于 0 毫秒");
        }
    }
}
