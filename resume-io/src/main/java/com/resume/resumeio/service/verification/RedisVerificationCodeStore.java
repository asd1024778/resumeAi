/*
 * 包声明：验证码存储实现所在包。
 */
package com.resume.resumeio.service.verification;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 版验证码存储。
 * <p>
 * 直接复用 Spring 的 {@link StringRedisTemplate}，利用 Redis 原生 TTL 实现
 * 「验证码自动过期」，多实例部署时天然共享。
 * <p>
 * Key 形如 {@code sms:code:register:13812345678} / {@code sms:attempts:register:13812345678}。
 */
public class RedisVerificationCodeStore implements VerificationCodeStore {

    /** Spring Redis 字符串操作模板 */
    private final StringRedisTemplate redisTemplate;

    /**
     * 构造器注入 Redis 模板。
     *
     * @param redisTemplate 字符串操作模板
     */
    public RedisVerificationCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * SET key value EX ttl，由 Redis 负责过期。
     */
    @Override
    public void save(String key, String value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            redisTemplate.opsForValue().set(key, value);
            return;
        }
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /**
     * GET key，不存在（或已过期）返回 empty。
     */
    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    /**
     * EXISTS key。
     */
    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * DEL key。
     */
    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * INCR：Redis 原生原子自增；首次自增后补上过期时间。
     */
    @Override
    public long increment(String key, Duration ttl) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            return 0L;
        }
        // 只在第一次自增时设置 TTL，之后不重置，保证与验证码同时失效
        if (count == 1L && ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            redisTemplate.expire(key, ttl);
        }
        return count;
    }
}
