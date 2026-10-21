/*
 * 包声明：Spring 配置类所在包，集中管理安全、Web、AI 等 Bean 定义。
 */
package com.resume.resumeio.config;

/*
 * 导入说明：
 * - JwtAuthenticationManager / JwtServerAuthenticationConverter：自定义 JWT 认证链组件
 * - Configuration / Bean：声明 Spring 配置与 Bean 工厂方法
 * - HttpMethod / HttpStatus：HTTP 方法与状态码常量
 * - EnableWebFluxSecurity / ServerHttpSecurity 等：WebFlux 响应式安全框架
 * - BCryptPasswordEncoder：密码哈希算法实现
 * - CorsConfiguration 等：跨域资源共享配置
 */
import com.resume.resumeio.security.JwtAuthenticationManager;
import com.resume.resumeio.security.JwtServerAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * WebFlux 安全配置：JWT 认证过滤器、路由权限、CORS、密码编码器。
 * <p>
 * 所有 /api/** 接口（除登录/注册/dev）均需携带有效 Bearer Token。
 */
@Configuration // 标记为 Spring 配置类
@EnableWebFluxSecurity // 启用 WebFlux 版 Spring Security
public class SecurityConfig {

    /** JWT 认证管理器：校验 Token 并构建 Authentication 对象 */
    private final JwtAuthenticationManager jwtAuthenticationManager;

    /** 请求转换器：从 Authorization 头提取 Bearer Token */
    private final JwtServerAuthenticationConverter jwtAuthenticationConverter;

    /**
     * 构造注入 JWT 相关组件（Spring 自动装配）。
     */
    public SecurityConfig(
            JwtAuthenticationManager jwtAuthenticationManager,
            JwtServerAuthenticationConverter jwtAuthenticationConverter
    ) {
        this.jwtAuthenticationManager = jwtAuthenticationManager;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    /**
     * 定义安全过滤器链：禁用 CSRF/表单登录，挂载 JWT 过滤器，配置路由授权规则。
     *
     * @param http ServerHttpSecurity 构建器
     * @return 组装完成的安全过滤器链
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        // 创建 JWT 认证过滤器，并绑定认证管理器与 Token 转换器
        AuthenticationWebFilter jwtFilter = new AuthenticationWebFilter(jwtAuthenticationManager);
        jwtFilter.setServerAuthenticationConverter(jwtAuthenticationConverter);

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // 无状态 API，关闭 CSRF
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable) // 不使用 HTTP Basic
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable) // 不使用表单登录
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // 启用 CORS
                .exceptionHandling(ex -> ex
                        // 未认证：返回 401
                        .authenticationEntryPoint((exchange, e) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        })
                        // 已认证但无权限：返回 403
                        .accessDeniedHandler((exchange, e) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        }))
                .authorizeExchange(auth -> auth
                        // 登录、注册、发送注册验证码接口公开
                        .pathMatchers(HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/sms-code").permitAll()
                        // 开发测试接口公开
                        .pathMatchers("/api/dev/**").permitAll()
                        // 其余 API 需认证
                        .pathMatchers("/api/**").authenticated()
                        // 静态资源等其它路径放行
                        .anyExchange().permitAll())
                // 将 JWT 过滤器插入认证阶段
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    /**
     * 密码编码器 Bean，注册/登录时对密码做 BCrypt 哈希。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS 跨域配置：允许本地 Vite 前端（5173 端口）访问 /api/**。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许的前端源地址
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // 允许携带 Cookie / Authorization
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
