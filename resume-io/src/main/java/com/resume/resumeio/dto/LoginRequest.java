package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 用户登录请求体。
 * <p>
 * 对应 POST 登录接口，前端提交用户名与密码进行身份验证。
 */
@Data
public class LoginRequest {

    /** 登录用户名 */
    private String username;

    /** 登录密码（明文，后端会与存储的哈希值比对） */
    private String password;
}
