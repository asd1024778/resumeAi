package com.resume.resumeio.service.sensitive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.resumeio.dto.UserSensitiveProfileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * 基于 Redis 的用户敏感资料动态存储实现。
 * <p>
 * 业务职责：将用户敏感信息以 JSON 字符串形式存入 Redis，
 * 与 MySQL 等持久化账号表分离，支持 TTL 自动过期，降低敏感数据长期滞留风险。
 * <p>
 * Redis Key 格式：{@code user:sensitive:{userId}}，例如 {@code user:sensitive:42}。
 */
public class RedisSensitiveProfileStore implements SensitiveProfileStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSensitiveProfileStore.class);
    /** Redis Key 前缀，后接 userId 组成完整键 */
    private static final String KEY_PREFIX = "user:sensitive:";

    /** Spring Redis 字符串操作模板，负责 GET/SET/DEL */
    private final StringRedisTemplate redisTemplate;
    /** Jackson 序列化/反序列化 DTO 与 JSON 互转 */
    private final ObjectMapper objectMapper;
    /** Key 过期时间；为零或负数表示永不过期 */
    private final Duration ttl;

    /**
     * 构造 Redis 存储实现，注入 Redis 客户端、JSON 工具与 TTL 策略。
     */
    public RedisSensitiveProfileStore(StringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper,
                                      Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    /**
     * 从 Redis 读取用户敏感资料并反序列化为 DTO。
     * 读取或解析失败时记录警告并返回 empty，不向上抛异常以免影响主流程。
     */
    @Override
    public Optional<UserSensitiveProfileDto> findByUserId(Long userId) {
        try {
            // Redis GET：按 user:sensitive:{userId} 取 JSON 字符串
            String json = redisTemplate.opsForValue().get(key(userId));
            // 键不存在或值为空串，视为无资料
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            // JSON → UserSensitiveProfileDto
            return Optional.of(objectMapper.readValue(json, UserSensitiveProfileDto.class));
        } catch (Exception e) {
            // Redis 网络异常、JSON 格式错误等：降级为「无资料」，避免阻断登录等业务
            log.warn("从 Redis 读取用户 {} 敏感资料失败: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 将敏感资料序列化为 JSON 写入 Redis。
     * 根据 TTL 配置决定是否设置过期时间。
     */
    @Override
    public void save(Long userId, UserSensitiveProfileDto profile) {
        try {
            // DTO → JSON 字符串
            String json = objectMapper.writeValueAsString(profile);
            // TTL 为零或负数：永久存储；否则 SET + EXPIRE（通过 set 的 Duration 参数）
            if (ttl.isZero() || ttl.isNegative()) {
                redisTemplate.opsForValue().set(key(userId), json);
            } else {
                redisTemplate.opsForValue().set(key(userId), json, ttl);
            }
        } catch (JsonProcessingException e) {
            // 序列化失败属于编程/数据错误，抛出运行时异常
            throw new IllegalStateException("敏感资料序列化失败", e);
        }
    }

    /**
     * 删除 Redis 中该用户的敏感资料 Key。
     */
    @Override
    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    /**
     * 生成 Redis 完整 Key：前缀 + 用户 ID。
     *
     * @param userId 用户主键
     * @return 例如 user:sensitive:123
     */
    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
