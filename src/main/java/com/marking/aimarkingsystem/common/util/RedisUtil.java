package com.marking.aimarkingsystem.common.util;

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
 */
@Component
public class RedisUtil {

    private final StringRedisTemplate redisTemplate;

    public RedisUtil(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== 字符串 ====================

    /** 写入，永不过期（慎用，一般配合 expire 或带超时的重载） */
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /** 写入并同时设置过期时间 */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /** 读取，不存在返回 null */
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /** 仅当 key 不存在时写入（可用于登录互踢、分布式锁雏形），返回是否写入成功 */
    public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }

    /** 自增，常用于验证码错误次数、接口限流计数 */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    // ==================== 删除 ====================

    /** 删除单个 key，返回是否删除成功 */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /** 批量删除，返回删除的数量 */
    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    // ==================== 过期时间 ====================

    /** 为已存在的 key 设置过期时间 */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /** 查询剩余过期时间，key 不存在返回 -2，无过期时间返回 -1 */
    public Long getExpire(String key, TimeUnit unit) {
        return redisTemplate.getExpire(key, unit);
    }

    /** 判断 key 是否存在 */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }
}
