/*
 * 包声明：JWT 认证与安全工具所在包。
 */
package com.resume.resumeio.security;

/*
 * 导入说明：
 * - ReactiveSecurityContextHolder：WebFlux 响应式安全上下文持有者
 * - Mono：异步单值流
 */
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

/**
 * 安全上下文工具类。
 * <p>
 * 在 WebFlux 链式调用中便捷获取当前登录用户 ID，
 * 无需手动从 ServerWebExchange 解析 Token。
 */
public final class SecurityUtils {

    /** 工具类禁止实例化 */
    private SecurityUtils() {
    }

    /**
     * 从当前 Reactor 上下文中的 SecurityContext 读取已认证用户 ID。
     *
     * @return 用户 ID 的 Mono；未认证时可能为空或报错（取决于调用链）
     */
    public static Mono<Long> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> (Long) ctx.getAuthentication().getPrincipal()); // principal 由 JwtAuthenticationManager 设为 userId
    }
}
