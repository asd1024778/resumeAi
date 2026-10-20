/*
 * 包声明：Spring 配置类所在包。
 */
package com.resume.resumeio.config;

/*
 * 导入说明：
 * - UserSensitiveProfileDto：用户敏感资料传输对象
 * - UserEntity / UserRepository：用户实体与持久层
 * - UserSensitiveProfileService：敏感资料读写服务
 * - ConditionalOnProperty：按配置属性决定是否加载本配置
 * - CommandLineRunner：启动完成后执行初始化逻辑
 * - PasswordEncoder：密码哈希工具
 */
import com.resume.resumeio.dto.UserSensitiveProfileDto;
import com.resume.resumeio.entity.UserEntity;
import com.resume.resumeio.repository.UserRepository;
import com.resume.resumeio.service.UserSensitiveProfileService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 演示数据初始化器。
 * <p>
 * 应用启动时创建 demo 用户（用户名 demo / 密码 123456）及示例敏感资料，
 * 便于本地开发与联调。可通过配置 {@code app.demo-data.enabled=false} 关闭。
 */
@Configuration
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer {

    /**
     * 注册启动 Runner，委托 {@link #initDemoData} 执行实际初始化。
     */
    @Bean
    CommandLineRunner initDemoUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserSensitiveProfileService sensitiveProfileService
    ) {
        return args -> initDemoData(userRepository, passwordEncoder, sensitiveProfileService);
    }

    /**
     * 初始化 demo 用户与敏感资料。
     * <p>
     * 使用 synchronized 防止多实例或重复 Runner 并发插入重复数据。
     */
    private static synchronized void initDemoData(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserSensitiveProfileService sensitiveProfileService
    ) {
        // 查找 demo 用户，不存在则创建
        UserEntity user = userRepository.findByUsername("demo").orElseGet(() -> {
            UserEntity created = new UserEntity();
            created.setUsername("demo");
            created.setPasswordHash(passwordEncoder.encode("123456")); // BCrypt 哈希存储
            created.setEmail("demo@example.com");
            return userRepository.save(created);
        });

        // 若尚无敏感资料，写入演示数据
        if (sensitiveProfileService.getProfile(user.getId()).isEmpty()) {
            sensitiveProfileService.saveProfile(user.getId(), UserSensitiveProfileDto.builder()
                    .realName("张三")
                    .phone("13812345678")
                    .idCard("110101199001011234")
                    .address("北京市朝阳区建国路88号")
                    .socialAccount("wechat_demo_2024")
                    .build());
        }
    }
}
