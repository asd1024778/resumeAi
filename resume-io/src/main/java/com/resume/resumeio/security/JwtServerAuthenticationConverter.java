/*
 * 包声明：JWT 认证与安全工具所在包。
 */
package com.resume.resumeio.security;

/*
 * 导入说明：
 * - HttpHeaders：HTTP 头常量（含 AUTHORIZATION）
 * - ServerAuthenticationConverter：将请求转换为 Authentication 的 WebFlux 接口
 * - ServerWebExchange：单次 HTTP 请求/响应上下文
 */
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT 请求认证转换器。
 * <p>
 * 从 {@code Authorization: Bearer <token>} 头提取 Token，
 * 封装为 {@link UsernamePasswordAuthenticationToken} 供 {@link JwtAuthenticationManager} 校验。
 */
@Component
public class JwtServerAuthenticationConverter implements ServerAuthenticationConverter {

    /**
     * 将入站请求转换为待认证的 Authentication 对象。
     *
     * @param exchange 当前 Web 交换对象
     * @return 含 Token 的 Authentication Mono；无 Bearer 头时返回空 Mono
     */
    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        // 缺少 Authorization 或非 Bearer  scheme，跳过 JWT 认证
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.empty();
        }
        String token = authHeader.substring(7); // 去掉 "Bearer " 前缀（7 个字符）
        // principal 与 credentials 均设为 token，供 AuthenticationManager 使用 credentials
        return Mono.just(new UsernamePasswordAuthenticationToken(token, token));
    }
}
