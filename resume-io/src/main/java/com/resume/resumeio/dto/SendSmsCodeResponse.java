package com.resume.resumeio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送短信验证码响应体。
 * <p>
 * 说明：当前未接入真实短信网关，「验证码」只写后端日志；
 * 开发期通过 {@link #debugCode} 直接回传给前端以便联调，
 * 接入网关后把 {@code app.sms.debug-return-code} 改为 false 即可（该字段变为 null）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendSmsCodeResponse {

    /** 实际接收验证码的手机号（规范化后的 11 位） */
    private String phone;

    /** 验证码有效期（秒） */
    private int expiresInSeconds;

    /** 下次可重新发送的间隔（秒），前端用于倒计时 */
    private int resendAfterSeconds;

    /** 开发期回传的验证码；正式环境为 null */
    private String debugCode;
}
