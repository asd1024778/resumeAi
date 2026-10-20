/*
 * 包声明：Spring 配置类所在包。
 */
package com.resume.resumeio.config;

/*
 * 导入说明：
 * - ObjectMapper：JSON 序列化/反序列化
 * - InMemorySensitiveProfileStore / RedisSensitiveProfileStore：敏感资料存储实现
 * - SensitiveProfileStore：存储接口
 * - ConditionalOnProperty：按 app.sensitive.storage 选择实现
 * - RedisConnectionFactory / StringRedisTemplate：Redis 连接与操作
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.resumeio.service.sensitive.InMemorySensitiveProfileStore;
import com.resume.resumeio.service.sensitive.RedisSensitiveProfileStore;
import com.resume.resumeio.service.sensitive.SensitiveProfileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 用户敏感资料存储后端配置。
 * <p>
 * 支持两种模式（由 {@code app.sensitive.storage} 控制）：
 * <ul>
 *   <li>{@code memory}（默认）：进程内内存存储，适合开发测试</li>
 *   <li>{@code redis}：Redis 持久化，Redis 不可用时自动回退到内存</li>
 * </ul>
 */
@Configuration
public class SensitiveStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(SensitiveStorageConfig.class);

    /**
     * Redis 存储实现：配置为 redis 时生效。
     * 启动时 ping Redis，失败则降级为内存存储。
     *
     * @param connectionFactory Redis 连接工厂
     * @param objectMapper      JSON 转换器
     * @param ttlDays           数据过期天数，0 表示永不过期
     * @return 敏感资料存储 Bean
     */
    @Bean
    @ConditionalOnProperty(name = "app.sensitive.storage", havingValue = "redis")
    public SensitiveProfileStore redisSensitiveProfileStore(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            @Value("${app.sensitive.redis-ttl-days:30}") int ttlDays
    ) {
        try {
            connectionFactory.getConnection().ping(); // 探测 Redis 是否可用
            Duration ttl = ttlDays > 0 ? Duration.ofDays(ttlDays) : Duration.ZERO;
            log.info("敏感资料存储：Redis（TTL={} 天）", ttlDays > 0 ? ttlDays : "永久");
            return new RedisSensitiveProfileStore(new StringRedisTemplate(connectionFactory), objectMapper, ttl);
        } catch (Exception e) {
            // Redis 连接失败，回退内存模式，保证应用可启动
            log.warn("Redis 不可用，回退到内存存储: {}", e.getMessage());
            return new InMemorySensitiveProfileStore();
        }
    }

    /**
     * 内存存储实现：默认模式或显式配置 memory 时生效。
     */
    @Bean
    @ConditionalOnProperty(name = "app.sensitive.storage", havingValue = "memory", matchIfMissing = true)
    public SensitiveProfileStore inMemorySensitiveProfileStore() {
        log.info("敏感资料存储：内存（开发/测试模式）");
        return new InMemorySensitiveProfileStore();
    }
}
