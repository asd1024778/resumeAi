package com.resume.resumeio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录成功后的响应体。
 * <p>
 * 包含 JWT 令牌与用户基础信息，前端需将 token 存入本地用于后续鉴权。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** JWT 访问令牌，后续请求需在 Authorization 头中携带 */
    private String token;

    /** 登录用户的基础信息（默认脱敏） */
    private UserDto user;
}
