/*
 * 包声明：JWT 认证与安全工具所在包。
 */
package com.resume.resumeio.security;

/*
 * 导入说明：
 * - Claims：JWT 载荷声明
 * - ReactiveAuthenticationManager：WebFlux 响应式认证管理器接口
 * - UsernamePasswordAuthenticationToken：Spring Security 认证令牌
 * - Mono：Reactor 单元素异步类型
 */
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;

/**
 * JWT 响应式认证管理器。
 * <p>
 * 由 {@link JwtServerAuthenticationConverter} 提取 Token 后，
 * 本类负责解析、校验并构建已认证的 {@link Authentication} 对象。
 */
@Component
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    /** JWT 生成与解析工具 */
    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationManager(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 认证入口：解析 Token，成功则返回含 userId 的 Authentication，失败返回空 Mono。
     *
     * @param authentication credentials 字段存放原始 JWT 字符串
     * @return 认证成功 Mono 或空 Mono（视为未认证）
     */
    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        try {
            Claims claims = jwtTokenProvider.parseToken(token); // 验签并解析载荷
            Long userId = jwtTokenProvider.getUserId(claims);
            String username = claims.get("username", String.class);
            // principal 设为 userId，details 存放 username 供业务层使用
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            auth.setDetails(username);
            return Mono.just(auth);
        } catch (Exception e) {
            return Mono.empty(); // Token 无效或过期，不抛异常，由 Security 返回 401
        }
    }
}
