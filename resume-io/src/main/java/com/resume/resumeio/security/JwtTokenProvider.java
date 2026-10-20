/*
 * 包声明：JWT 认证与安全工具所在包。
 */
package com.resume.resumeio.security;

/*
 * 导入说明：
 * - Claims / Jwts / Keys：JJWT 库，用于 JWT 构建、解析与 HMAC 密钥
 * - Value：注入 application.properties 配置项
 * - SecretKey / Date：签名密钥与过期时间
 */
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌工具类：生成、解析、提取用户信息。
 * <p>
 * 密钥与过期时间从配置文件 {@code jwt.secret}、{@code jwt.expiration-ms} 读取。
 */
@Component
public class JwtTokenProvider {

    /** HMAC-SHA 签名密钥 */
    private final SecretKey secretKey;

    /** Token 有效时长（毫秒） */
    private final long expirationMs;

    /**
     * 从配置构造 Provider，将字符串 secret 转为 SecretKey。
     */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * 为用户生成 JWT。
     *
     * @param userId   用户 ID，写入 subject
     * @param username 用户名，写入自定义 claim
     * @return 紧凑格式的 JWT 字符串
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并验签 JWT，返回载荷 Claims。
     *
     * @param token JWT 字符串
     * @return 已验证的 Claims
     * @throws io.jsonwebtoken.JwtException 签名无效或已过期
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Claims 中提取用户 ID（subject 字段）。
     */
    public Long getUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }
}
