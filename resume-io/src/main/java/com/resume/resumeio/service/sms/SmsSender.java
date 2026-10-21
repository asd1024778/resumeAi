/*
 * 包声明：短信发送通道所在包。
 */
package com.resume.resumeio.service.sms;

/**
 * 短信发送通道抽象（预留扩展点）。
 * <p>
 * 当前只有 {@link LoggingSmsSender} 一个实现：把验证码写进日志、不真正发短信，
 * 满足本地联调需要。后续接入真实网关（阿里云 SMS、腾讯云 SMS、华为云等）时，
 * 只需新增一个实现类并声明为 Bean，业务层的
 * {@code SmsCodeService} 无需任何改动。
 * <p>
 * 典型实现签名（伪代码）：
 * <pre>
 * &#64;Service
 * public class AliyunSmsSender implements SmsSender {
 *     public void sendVerificationCode(String phone, String code, int ttlSeconds) {
 *         client.sendSms(new SendSmsRequest()
 *                 .setPhoneNumbers(phone)
 *                 .setTemplateCode("SMS_xxxxxx")
 *                 .setTemplateParam(Map.of("code", code)));
 *     }
 * }
 * </pre>
 */
public interface SmsSender {

    /**
     * 下发验证码短信。
     * <p>
     * 实现方需保证：失败时抛出运行时异常，让调用方感知发送失败；
     * 不得在日志中打印完整手机号（按项目约定脱敏）。
     *
     * @param phone      接收号码（已规范化的 11 位手机号）
     * @param code       验证码（6 位纯数字）
     * @param ttlSeconds 验证码有效期（秒），用于短信文案提示
     */
    void sendVerificationCode(String phone, String code, int ttlSeconds);
}
