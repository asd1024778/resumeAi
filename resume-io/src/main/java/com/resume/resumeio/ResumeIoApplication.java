/*
 * 包声明：根包 com.resume.resumeio，所有业务代码均在此包及其子包下。
 */
package com.resume.resumeio;

/*
 * 导入说明：
 * - SpringApplication：Spring Boot 启动引导类，负责创建并运行应用上下文
 * - SpringBootApplication：组合注解，启用自动配置、组件扫描等
 * - EnableJpaRepositories：显式启用 JPA 仓库扫描，指定 repository 包路径
 */
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Resume-IO 后端应用启动入口。
 * <p>
 * 相当于传统 Java 程序的 main 函数所在类。运行 {@link #main(String[])} 后，
 * Spring Boot 会自动扫描组件、加载配置、启动 WebFlux 服务器（默认 8080 端口）。
 */
@SpringBootApplication // 标记为 Spring Boot 主应用，触发自动配置与组件扫描
@EnableJpaRepositories(basePackages = "com.resume.resumeio.repository") // 扫描 JPA Repository 接口
public class ResumeIoApplication {

    /**
     * 程序入口：由 JVM 调用，启动整个 Spring 容器。
     *
     * @param args 命令行参数（如 --spring.profiles.active=dev）
     */
    public static void main(String[] args) {
        // 以当前类为配置源，创建 ApplicationContext 并阻塞运行直至进程退出
        SpringApplication.run(ResumeIoApplication.class, args);
    }

}
