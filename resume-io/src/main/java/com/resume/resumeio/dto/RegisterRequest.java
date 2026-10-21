package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 用户注册请求体。
 * <p>
 * 对应 {@code POST /api/auth/register}。手机号注册主流程：
 * 先调 {@code POST /api/auth/sms-code} 拿验证码，再带 {@link #phone} + {@link #code} 提交注册。
 */
@Data
public class RegisterRequest {

    /** 注册用户名，留空则默认使用手机号（仍需全局唯一） */
    private String username;

    /** 注册密码（明文，后端会哈希后存储） */
    private String password;

    /** 手机号（必填，注册主身份，会通过短信验证码校验后写入 users 表） */
    private String phone;

    /** 短信验证码（必填，6 位纯数字，一次性使用） */
    private String code;

    /** 用户邮箱，可选 */
    private String email;

    /** 真实姓名（可选，注册时一并提交，存入 Redis/内存） */
    private String realName;

    /** 身份证号码（可选） */
    private String idCard;

    /** 联系地址（可选） */
    private String address;

    /** 社交账号，如微信、QQ（可选） */
    private String socialAccount;
}
