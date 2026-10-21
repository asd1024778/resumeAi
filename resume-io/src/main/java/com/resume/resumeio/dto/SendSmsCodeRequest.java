package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 发送短信验证码请求体。
 * <p>
 * 对应 {@code POST /api/auth/sms-code}。
 */
@Data
public class SendSmsCodeRequest {

    /** 接收验证码的手机号（后端会做规范化 + 格式校验） */
    private String phone;
}
