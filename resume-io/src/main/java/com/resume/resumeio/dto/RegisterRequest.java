package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 用户注册请求体。
 * <p>
 * 对应 POST 注册接口，创建新账户并可一并提交敏感资料。
 */
@Data
public class RegisterRequest {

    /** 注册用户名，需全局唯一 */
    private String username;

    /** 注册密码（明文，后端会哈希后存储） */
    private String password;

    /** 用户邮箱，可选 */
    private String email;

    /** 真实姓名（可选，注册时一并提交，存入 Redis/内存） */
    private String realName;

    /** 手机号码（可选） */
    private String phone;

    /** 身份证号码（可选） */
    private String idCard;

    /** 联系地址（可选） */
    private String address;

    /** 社交账号，如微信、QQ（可选） */
    private String socialAccount;
}
