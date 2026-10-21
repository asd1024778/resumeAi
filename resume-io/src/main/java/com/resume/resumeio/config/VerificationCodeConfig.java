/*
 * 包声明：Spring 配置类所在包。
 */
package com.resume.resumeio.config;

import com.resume.resumeio.service.verification.InMemoryVerificationCodeStore;
import com.resume.resumeio.service.verification.RedisVerificationCodeStore;
import com.resume.resumeio.service.verification.VerificationCodeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 短信验证码存储后端配置。
 * <p>
 * 由 {@code app.sms.storage} 控制实现：
 * <ul>
 *   <li>{@code redis}（默认）：多实例共享 + 原生 TTL；Redis 不可用时自动降级</li>
 *   <li>{@code memory}：进程内 Map，dev profile 使用</li>
 * </ul>
 * 这里用 {@link ObjectProvider} 获取 Redis 模板，是为了在
 * {@code spring.autoconfigure.exclude=RedisAutoConfiguration}（dev profile）时
 * 不会因为缺少 Bean 导致启动失败。
 */
@Configuration
public class VerificationCodeConfig {

    private static final Logger log = LoggerFactory.getLogger(VerificationCodeConfig.class);

    /**
     * 构造验证码存储 Bean。
     *
     * @param storage       期望的存储类型（redis / memory）
     * @param redisProvider Redis 模板提供者，可能不存在
     * @return 可用的验证码存储实现
     */
    @Bean
    public VerificationCodeStore verificationCodeStore(
            @Value("${app.sms.storage:redis}") String storage,
            ObjectProvider<StringRedisTemplate> redisProvider
    ) {
        if ("redis".equalsIgnoreCase(storage)) {
            StringRedisTemplate template = redisProvider.getIfAvailable();
            if (template == null) {
                log.warn("app.sms.storage=redis，但当前上下文没有 Redis 客户端，验证码改用内存存储");
                return new InMemoryVerificationCodeStore();
            }
            try {
                // 启动即探测一次，避免运行时才发现 Redis 不通
                template.getConnectionFactory().getConnection().ping();
                log.info("短信验证码存储：Redis");
                return new RedisVerificationCodeStore(template);
            } catch (Exception e) {
                log.warn("Redis 不可用，验证码回退到内存存储: {}", e.getMessage());
                return new InMemoryVerificationCodeStore();
            }
        }
        log.info("短信验证码存储：内存（开发/测试模式）");
        return new InMemoryVerificationCodeStore();
    }
}
