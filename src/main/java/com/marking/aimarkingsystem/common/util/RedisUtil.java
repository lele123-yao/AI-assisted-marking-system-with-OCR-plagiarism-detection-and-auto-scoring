package com.marking.aimarkingsystem.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Redis 通用工具类。
 *
 * 说明：
 * - 基于 StringRedisTemplate，所有值统一按字符串存储；
 *   存对象时由调用方先序列化为 JSON（可用 Hutool 的 JSONUtil.toJsonStr），
 *   读取时再反序列化。好处是 Redis 中数据人类可读，便于调试；
 * - 只做"原子能力"封装，不写任何业务 Key 规则（如 login:token:xxx），
 *   Key 的命名由各业务模块自行约定，保证本类长期不用修改。
 *
 * 容错设计（重要）：
 * - 所有方法对 Redis 异常做兜底：失败时返回类型默认值并记录 warn 日志，不向上抛出。
 * - 目的是让 Redis 在本系统中"可选"：
 *     · Redis 可用时，提供"后登录挤掉先登录"、接口限流等增强能力；
 *     · Redis 不可用时，降级为纯 JWT 无状态模式，登录、鉴权、业务接口全部照常工作，
 *       仅失去主动失效 token 与限流能力，不会因中间件缺失导致整个网站不可用。
 */
@Component
public class RedisUtil {

    private static final Logger log = LoggerFactory.getLogger(RedisUtil.class);

    private final StringRedisTemplate redisTemplate;

    public RedisUtil(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== 字符串 ====================

    /** 写入，永不过期（慎用，一般配合 expire 或带超时的重载） */
    public void set(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.warn("Redis set 失败（已降级忽略）: key={}, err={}", key, e.getMessage());
        }
    }

    /** 写入并同时设置过期时间 */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.warn("Redis set 失败（已降级忽略）: key={}, err={}", key, e.getMessage());
        }
    }

    /** 读取，不存在或 Redis 异常返回 null */
    public String get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis get 失败（已降级返回 null）: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 仅当 key 不存在时写入（可用于登录互踢、分布式锁雏形），返回是否写入成功。
     * Redis 不可用时降级放行（返回 true）：调用方仍有数据库等兜底校验，
     * 若返回 false 会导致「Redis 未启动就完全无法交卷」，不符合演示环境要求。
     */
    public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        try {
            return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
        } catch (Exception e) {
            log.warn("Redis setIfAbsent 失败（已降级放行）: key={}, err={}", key, e.getMessage());
            return true;
        }
    }

    /** 自增，常用于验证码错误次数、接口限流计数；异常返回 null */
    public Long increment(String key, long delta) {
        try {
            return redisTemplate.opsForValue().increment(key, delta);
        } catch (Exception e) {
            log.warn("Redis increment 失败（已降级返回 null）: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    // ==================== 删除 ====================

    /** 删除单个 key，返回是否删除成功；异常返回 false */
    public Boolean delete(String key) {
        try {
            return redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis delete 失败（已降级返回 false）: key={}, err={}", key, e.getMessage());
            return false;
        }
    }

    /** 批量删除，返回删除的数量；异常返回 0 */
    public Long delete(Collection<String> keys) {
        try {
            return redisTemplate.delete(keys);
        } catch (Exception e) {
            log.warn("Redis delete(batch) 失败（已降级返回 0）: err={}", e.getMessage());
            return 0L;
        }
    }

    // ==================== 过期时间 ====================

    /** 为已存在的 key 设置过期时间；异常返回 false */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return redisTemplate.expire(key, timeout, unit);
        } catch (Exception e) {
            log.warn("Redis expire 失败（已降级返回 false）: key={}, err={}", key, e.getMessage());
            return false;
        }
    }

    /** 查询剩余过期时间，key 不存在返回 -2，无过期时间返回 -1；异常返回 null */
    public Long getExpire(String key, TimeUnit unit) {
        try {
            return redisTemplate.getExpire(key, unit);
        } catch (Exception e) {
            log.warn("Redis getExpire 失败（已降级返回 null）: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /** 判断 key 是否存在；异常返回 false */
    public Boolean hasKey(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            log.warn("Redis hasKey 失败（已降级返回 false）: key={}, err={}", key, e.getMessage());
            return false;
        }
    }
}
