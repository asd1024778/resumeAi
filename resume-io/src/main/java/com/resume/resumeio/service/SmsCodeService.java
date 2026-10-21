/*
 * 包声明：业务服务所在包。
 */
package com.resume.resumeio.service;

import com.resume.resumeio.dto.SendSmsCodeResponse;
import com.resume.resumeio.repository.UserRepository;
import com.resume.resumeio.service.sms.SmsSender;
import com.resume.resumeio.service.verification.VerificationCodeStore;
import com.resume.resumeio.util.PhoneUtils;
import com.resume.resumeio.util.SensitiveDataMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

/**
 * 短信验证码业务服务。
 * <p>
 * 本次实现的核心能力（登录/注册共用的验证码基础设施）：
 * <ol>
 *   <li><b>手机号格式校验</b>：{@link PhoneUtils}（{@code 1[3-9]\d{9}}）</li>
 *   <li><b>生成 6 位纯数字验证码</b>：{@link SecureRandom}，不使用可预测的 Random</li>
 *   <li><b>有效期</b>：默认 300 秒，存 Redis（不可用时退化为内存）</li>
 *   <li><b>发送冷却</b>：默认 60 秒内不允许重复发送，防刷</li>
 *   <li><b>错误次数限制</b>：默认 5 次，超限作废需重新获取</li>
 *   <li><b>一次性消费</b>：校验通过立即删除，防止重放</li>
 * </ol>
 * 优化预留：验证码当前存于 Redis/内存，若后续要支持「短信+图形验证码」「IP 限流」
 * 「指纹设备绑定」等，可在此基础上叠加，不影响调用方签名。
 */
@Service
public class SmsCodeService {

    private static final Logger log = LoggerFactory.getLogger(SmsCodeService.class);

    /** 验证码位数：需求固定为 6 位纯数字 */
    public static final int CODE_LENGTH = 6;

    /** 业务场景：注册（Key 中带场景，便于将来扩展登录/改密/绑定手机号） */
    private static final String SCENE_REGISTER = "register";

    /** Key 前缀：验证码本体 */
    private static final String CODE_KEY_PREFIX = "sms:code:";
    /** Key 前缀：错误尝试次数 */
    private static final String ATTEMPTS_KEY_PREFIX = "sms:attempts:";
    /** Key 前缀：发送冷却标记 */
    private static final String COOLDOWN_KEY_PREFIX = "sms:cooldown:";

    /** 6 位纯数字格式校验（前端约定一致） */
    private static final String CODE_PATTERN = "^\\d{" + CODE_LENGTH + "}$";

    /** 密码学安全随机源，避免验证码可被推测 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 验证码存储实现（Redis / 内存） */
    private final VerificationCodeStore codeStore;
    /** 用户仓储，用于校验手机号是否已注册 */
    private final UserRepository userRepository;
    /** 短信通道（当前为日志实现，预留真实网关） */
    private final SmsSender smsSender;

    /** 验证码有效期（秒） */
    private final int codeTtlSeconds;
    /** 重新发送冷却时间（秒） */
    private final int resendIntervalSeconds;
    /** 单个验证码允许的最大错误次数 */
    private final int maxVerifyAttempts;
    /** 是否把验证码回传给前端（仅开发期，接入真实网关后置为 false） */
    private final boolean debugReturnCode;

    /**
     * 构造器注入依赖与配置项。
     */
    public SmsCodeService(
            VerificationCodeStore codeStore,
            UserRepository userRepository,
            SmsSender smsSender,
            @Value("${app.sms.code-ttl-seconds:300}") int codeTtlSeconds,
            @Value("${app.sms.resend-interval-seconds:60}") int resendIntervalSeconds,
            @Value("${app.sms.max-verify-attempts:5}") int maxVerifyAttempts,
            @Value("${app.sms.debug-return-code:true}") boolean debugReturnCode
    ) {
        this.codeStore = codeStore;
        this.userRepository = userRepository;
        this.smsSender = smsSender;
        this.codeTtlSeconds = codeTtlSeconds;
        this.resendIntervalSeconds = resendIntervalSeconds;
        this.maxVerifyAttempts = maxVerifyAttempts;
        this.debugReturnCode = debugReturnCode;
        log.info("短信验证码策略：有效期 {}s，冷却 {}s，最大错误 {} 次，回传验证码={}",
                codeTtlSeconds, resendIntervalSeconds, maxVerifyAttempts, debugReturnCode);
    }

    /**
     * 发送注册验证码。
     * <p>
     * 校验顺序：手机号格式 → 是否已注册 → 发送冷却 → 生成并下发。
     *
     * @param rawPhone 用户输入的手机号
     * @return 发送结果（含有效期、冷却时间；开发期含验证码本身）
     * @throws IllegalArgumentException 手机号非法、已注册或仍在冷却期
     */
    public SendSmsCodeResponse sendRegisterCode(String rawPhone) {
        // 1. 手机号格式校验（核心逻辑，前后端各做一次，后端为准）
        String phone = PhoneUtils.requireValid(rawPhone);

        // 2. 已注册的号码不再发码，避免无意义短信与账号重复
        if (userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("该手机号已注册，请直接登录");
        }

        // 3. 发送冷却：记录上次发送时间，剩余秒数回显给用户
        String cooldownKey = cooldownKey(SCENE_REGISTER, phone);
        long nowSeconds = System.currentTimeMillis() / 1000;
        Optional<String> cooldown = codeStore.find(cooldownKey);
        if (cooldown.isPresent()) {
            long elapsed = nowSeconds - parseLong(cooldown.get());
            long remain = resendIntervalSeconds - elapsed;
            if (remain > 0) {
                throw new IllegalArgumentException("验证码发送过于频繁，请 " + remain + " 秒后再试");
            }
        }

        // 4. 生成 6 位纯数字验证码并存储（成功后才写冷却标记，避免发送失败也锁死用户）
        String code = generateCode();
        codeStore.save(codeKey(SCENE_REGISTER, phone), code, Duration.ofSeconds(codeTtlSeconds));
        codeStore.delete(attemptsKey(SCENE_REGISTER, phone)); // 重新获取时重置错误计数
        codeStore.save(cooldownKey, String.valueOf(nowSeconds), Duration.ofSeconds(resendIntervalSeconds));

        // 5. 下发（当前为日志通道，真实网关只需替换 SmsSender 实现）
        smsSender.sendVerificationCode(phone, code, codeTtlSeconds);

        return new SendSmsCodeResponse(
                phone,
                codeTtlSeconds,
                resendIntervalSeconds,
                debugReturnCode ? code : null
        );
    }

    /**
     * 校验注册验证码，通过后立即作废（一次性使用）。
     * <p>
     * 失败分三种情况，均抛出 {@link IllegalArgumentException} 由 Controller 转成业务错误：
     * 未发送/已过期、错误但仍有剩余次数、错误次数超限。
     *
     * @param rawPhone 手机号
     * @param code     用户填写的验证码
     * @throws IllegalArgumentException 校验不通过
     */
    public void verifyRegisterCode(String rawPhone, String code) {
        // 手机号同样要校验，防止绕过「发码」接口直接调注册
        String phone = PhoneUtils.requireValid(rawPhone);

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("请输入验证码");
        }
        String input = code.trim();
        if (!input.matches(CODE_PATTERN)) {
            throw new IllegalArgumentException("验证码为 " + CODE_LENGTH + " 位数字");
        }

        // 1. 取存储中的验证码（过期 / 未发送 / 已使用都视为不存在）
        String storedCode = codeStore.find(codeKey(SCENE_REGISTER, phone))
                .orElseThrow(() -> new IllegalArgumentException("验证码已过期或未发送，请重新获取"));

        // 2. 先看错误次数是否已超限（避免继续暴力尝试）
        long used = parseLong(codeStore.find(attemptsKey(SCENE_REGISTER, phone)).orElse("0"));
        if (used >= maxVerifyAttempts) {
            invalidate(SCENE_REGISTER, phone);
            throw new IllegalArgumentException("验证码错误次数过多，请重新获取验证码");
        }

        // 3. 定长比较，避免时序侧信道
        if (!constantTimeEquals(storedCode, input)) {
            long attempts = codeStore.increment(attemptsKey(SCENE_REGISTER, phone),
                    Duration.ofSeconds(codeTtlSeconds));
            long remain = maxVerifyAttempts - attempts;
            if (remain <= 0) {
                invalidate(SCENE_REGISTER, phone);
                throw new IllegalArgumentException("验证码错误次数过多，请重新获取验证码");
            }
            throw new IllegalArgumentException("验证码不正确，还可再试 " + remain + " 次");
        }

        // 4. 校验通过：立即删除验证码与计数，保证一次性使用
        invalidate(SCENE_REGISTER, phone);
        log.info("手机号 {} 的注册验证码校验通过", SensitiveDataMask.maskPhone(phone));
    }

    /**
     * 生成 6 位纯数字验证码（含前导 0，如 012345）。
     *
     * @return 定长 6 位数字串
     */
    private String generateCode() {
        // nextInt(1000000) 取值范围 0~999999，补零后恰好 6 位
        return String.format("%0" + CODE_LENGTH + "d", RANDOM.nextInt(1_000_000));
    }

    /**
     * 作废验证码：删除验证码本体、错误计数、冷却标记。
     * <p>
     * 冷却标记一并删除的原因：校验通过（或错误超限）后允许用户立刻重新获取。
     */
    private void invalidate(String scene, String phone) {
        codeStore.delete(codeKey(scene, phone));
        codeStore.delete(attemptsKey(scene, phone));
        codeStore.delete(cooldownKey(scene, phone));
    }

    /** 验证码 Key：sms:code:{scene}:{phone} */
    private String codeKey(String scene, String phone) {
        return CODE_KEY_PREFIX + scene + ":" + phone;
    }

    /** 错误计数 Key：sms:attempts:{scene}:{phone} */
    private String attemptsKey(String scene, String phone) {
        return ATTEMPTS_KEY_PREFIX + scene + ":" + phone;
    }

    /** 冷却标记 Key：sms:cooldown:{scene}:{phone} */
    private String cooldownKey(String scene, String phone) {
        return COOLDOWN_KEY_PREFIX + scene + ":" + phone;
    }

    /**
     * 定长字符串比较：逐字节比较且不提前返回，避免通过响应时间推测验证码。
     */
    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 安全解析 long，异常值按 0 处理。
     */
    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
