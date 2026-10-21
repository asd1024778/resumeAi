/*
 * 包声明：验证码存储抽象所在包。
 */
package com.resume.resumeio.service.verification;

import java.time.Duration;
import java.util.Optional;

/**
 * 验证码存储抽象。
 * <p>
 * 验证码是「短生命周期 + 高频读写」的数据，不适合落关系库，
 * 因此抽出一层存储接口，按环境选择实现：
 * <ul>
 *   <li>{@code redis}：多实例共享、自带 TTL（生产/本机 Docker 环境）</li>
 *   <li>{@code memory}：进程内 Map（dev / 单元测试，无 Redis 依赖）</li>
 * </ul>
 * Key 约定由调用方（{@code SmsCodeService}）拼装，形如
 * {@code sms:code:register:13812345678}。
 */
public interface VerificationCodeStore {

    /**
     * 写入键值并设置存活时间。
     *
     * @param key   完整 Key
     * @param value 值（验证码 / 计数 / 时间戳等）
     * @param ttl   存活时间，超过后自动失效
     */
    void save(String key, String value, Duration ttl);

    /**
     * 读取值，键不存在或已过期时返回 {@link Optional#empty()}。
     */
    Optional<String> find(String key);

    /**
     * 判断键是否存在且未过期。
     */
    boolean exists(String key);

    /**
     * 删除键（验证码一次性使用，校验通过后必须删除）。
     */
    void delete(String key);

    /**
     * 原子自增，用于统计「验证码错误尝试次数」。
     * <p>
     * 首次自增（结果 == 1）时需要顺带设置 TTL，避免计数器永不过期。
     *
     * @param key 计数器 Key
     * @param ttl 首次写入时的存活时间
     * @return 自增后的值
     */
    long increment(String key, Duration ttl);
}
