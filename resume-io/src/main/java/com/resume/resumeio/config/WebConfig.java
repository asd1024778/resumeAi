/*
 * 包声明：Spring 配置类所在包。
 */
package com.resume.resumeio.config;

/*
 * 导入说明：
 * - Configuration：标记本类为 Spring 配置类
 */
import org.springframework.context.annotation.Configuration;

/**
 * Web 全局配置占位类。
 * <p>
 * 当前 CORS 已在 {@link SecurityConfig} 中统一配置，避免与 Security 过滤器链冲突。
 * 若后续需要注册拦截器、格式化器等 Web 相关 Bean，可在此类中添加。
 */
@Configuration // 注册为 Spring 配置组件
public class WebConfig {
    // 暂无额外 Web 配置项
}
