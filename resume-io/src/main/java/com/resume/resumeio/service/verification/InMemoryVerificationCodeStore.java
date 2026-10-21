/*
 * 包声明：验证码存储实现所在包。
 */
package com.resume.resumeio.service.verification;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版验证码存储（dev / 测试环境，无需 Redis）。
 * <p>
 * 用 {@link ConcurrentHashMap} + 过期时间戳模拟 Redis 的 TTL 语义：
 * 读取时惰性判断是否过期，过期即视为不存在并顺手清理。
 * <p>
 * 局限：进程重启即丢失、多实例不共享，因此仅用于本地开发与单元测试。
 */
public class InMemoryVerificationCodeStore implements VerificationCodeStore {

    /**
     * 存储条目：值 + 绝对过期时间（epoch 毫秒，0 表示永不过期）。
     */
    private record Entry(String value, long expireAtMillis) {

        /**
         * 判断条目是否已过期。
         */
        boolean isExpired(long nowMillis) {
            return expireAtMillis > 0 && nowMillis >= expireAtMillis;
        }
    }

    /** 实际数据容器，Key 与 Redis 实现保持一致 */
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /**
     * 写入键值，按 ttl 计算绝对过期时间。
     */
    @Override
    public void save(String key, String value, Duration ttl) {
        store.put(key, new Entry(value, expireAt(ttl)));
    }

    /**
     * 读取未过期的值；已过期则删除并返回 empty。
     */
    @Override
    public Optional<String> find(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired(System.currentTimeMillis())) {
            store.remove(key, entry); // 惰性清理，避免内存无限增长
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    /**
     * 判断键是否存在且未过期。
     */
    @Override
    public boolean exists(String key) {
        return find(key).isPresent();
    }

    /**
     * 删除键。
     */
    @Override
    public void delete(String key) {
        store.remove(key);
    }

    /**
     * 原子自增：compute 保证同一 Key 的读改写是原子的。
     * <p>
     * 首次自增时按 ttl 设置过期时间；计数器已过期则按新值重新计时。
     */
    @Override
    public long increment(String key, Duration ttl) {
        long now = System.currentTimeMillis();
        long expireAt = expireAt(ttl);
        Entry updated = store.compute(key, (k, old) -> {
            if (old == null || old.isExpired(now)) {
                return new Entry("1", expireAt);
            }
            long next = parseLong(old.value()) + 1;
            // 已有计数器不刷新 TTL，保持与验证码同一生命周期
            return new Entry(String.valueOf(next), old.expireAtMillis());
        });
        return parseLong(updated.value());
    }

    /**
     * 把 TTL 换算成绝对过期时间；ttl 为零或负数表示永不过期。
     */
    private long expireAt(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return 0L;
        }
        return System.currentTimeMillis() + ttl.toMillis();
    }

    /**
     * 安全解析计数，异常值按 0 处理。
     */
    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
