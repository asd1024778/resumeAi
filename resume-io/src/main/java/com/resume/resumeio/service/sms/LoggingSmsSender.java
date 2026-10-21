/*
 * 包声明：短信发送通道所在包。
 */
package com.resume.resumeio.service.sms;

import com.resume.resumeio.util.SensitiveDataMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 日志版短信通道：不真正发短信，只把验证码打到应用日志里。
 * <p>
 * 用途：本地开发、联调、演示环境（无短信网关账号也能跑通注册流程）。
 * 定位：README 中「预留后续优化」的默认实现，接入真实网关后替换即可。
 */
@Component
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    /**
     * 打印模拟短信内容。
     * <p>
     * 为避免日志泄露隐私，手机号做脱敏打印（138****5678）；
     * 验证码本身需要可读，否则本地无法完成注册。
     */
    @Override
    public void sendVerificationCode(String phone, String code, int ttlSeconds) {
        log.info("【模拟短信】{} 的验证码为 {}，{} 秒内有效（未接入真实短信网关）",
                SensitiveDataMask.maskPhone(phone), code, ttlSeconds);
    }
}
