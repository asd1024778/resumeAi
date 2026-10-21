/*
 * 包声明：通用工具类所在包。
 */
package com.resume.resumeio.util;

import java.util.regex.Pattern;

/**
 * 中国大陆手机号工具类。
 * <p>
 * 职责：统一注册、登录等入口的手机号码规范化与格式校验，
 * 避免前端、后端各处重复写正则导致校验规则漂移。
 * <p>
 * 校验规则：11 位数字，1 开头，第二位为 3~9（即 {@code 1[3-9]\d{9}}）。
 * 同时兼容用户粘贴带空格、短横线或 {@code +86} / {@code 86} 前缀的写法。
 */
public final class PhoneUtils {

    /** 中国大陆手机号正则：1 开头 + 第二位 3~9 + 后 9 位数字 */
    private static final Pattern CN_MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 手机号中出现但不影响识别的分隔符（空格、短横线、横线、括号） */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-()]");

    /** 工具类禁止实例化 */
    private PhoneUtils() {
    }

    /**
     * 规范化手机号：去除分隔符与 +86 / 86 国家码。
     * <p>
     * 示例：{@code +86 138-1234-5678} → {@code 13812345678}
     *
     * @param raw 用户输入的原始手机号
     * @return 规范化后的字符串；入参为 null 时返回 null
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String phone = SEPARATORS.matcher(raw).replaceAll("").trim();
        if (phone.startsWith("+86")) {
            phone = phone.substring(3);
        } else if (phone.startsWith("86") && phone.length() == 13) {
            phone = phone.substring(2);
        }
        return phone;
    }

    /**
     * 判断是否为合法的中国大陆手机号（会先做规范化）。
     *
     * @param raw 原始手机号
     * @return true 表示格式合法
     */
    public static boolean isValid(String raw) {
        String phone = normalize(raw);
        return phone != null && CN_MOBILE_PATTERN.matcher(phone).matches();
    }

    /**
     * 规范化并强校验：不合法直接抛出 {@link IllegalArgumentException}。
     * <p>
     * 供 Service 层入口使用，由 Controller 统一捕获转成业务错误响应。
     *
     * @param raw 原始手机号
     * @return 规范化后的 11 位手机号
     * @throws IllegalArgumentException 格式不合法时抛出，消息可直接展示给用户
     */
    public static String requireValid(String raw) {
        String phone = normalize(raw);
        if (phone == null || phone.isEmpty()) {
            throw new IllegalArgumentException("请输入手机号");
        }
        if (!CN_MOBILE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("手机号格式不正确，请输入 11 位大陆手机号");
        }
        return phone;
    }
}
