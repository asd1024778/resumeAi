/*
 * 包声明：通用工具类所在包。
 */
package com.resume.resumeio.util;

/**
 * 敏感信息脱敏工具类。
 * <p>
 * 在对外 API 响应、日志输出等场景统一调用，避免手机号、身份证、地址等明文泄露。
 * 所有方法均为纯函数，对 null 或空白输入原样返回或做安全降级处理。
 */
public final class SensitiveDataMask {

    /** 工具类禁止实例化 */
    private SensitiveDataMask() {
    }

    /**
     * 姓名脱敏：保留首字，其余替换为 *。
     * <p>示例：张三 → 张*，欧阳娜娜 → 欧**</p>
     */
    public static String maskName(String name) {
        if (isBlank(name)) {
            return name;
        }
        String trimmed = name.trim();
        if (trimmed.length() == 1) {
            return "*"; // 单字姓名全部隐藏
        }
        return trimmed.charAt(0) + "*".repeat(trimmed.length() - 1);
    }

    /**
     * 手机号脱敏：保留前 3 后 4 位。
     * <p>示例：13812345678 → 138****5678</p>
     */
    public static String maskPhone(String phone) {
        if (isBlank(phone)) {
            return phone;
        }
        String digits = phone.replaceAll("\\D", ""); // 去除非数字字符
        if (digits.length() < 7) {
            return "***"; // 位数不足，完全隐藏
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    /**
     * 身份证号脱敏：保留前 6 后 4 位。
     * <p>示例：110101199001011234 → 110101********1234</p>
     */
    public static String maskIdCard(String idCard) {
        if (isBlank(idCard)) {
            return idCard;
        }
        String trimmed = idCard.trim();
        if (trimmed.length() <= 8) {
            return "*".repeat(trimmed.length()); // 过短则全部星号
        }
        return trimmed.substring(0, 6) + "*".repeat(trimmed.length() - 10) + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * 邮箱脱敏：保留本地部分首字符与完整域名。
     * <p>示例：demo@example.com → d***@example.com</p>
     */
    public static String maskEmail(String email) {
        if (isBlank(email) || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        if (local.length() <= 1) {
            return "*@" + parts[1];
        }
        return local.charAt(0) + "***@" + parts[1];
    }

    /**
     * 地址脱敏：保留前 6 个字符，后续用 **** 替代。
     */
    public static String maskAddress(String address) {
        if (isBlank(address)) {
            return address;
        }
        String trimmed = address.trim();
        if (trimmed.length() <= 6) {
            return trimmed.charAt(0) + "****";
        }
        return trimmed.substring(0, 6) + "****";
    }

    /**
     * 社交账号脱敏：保留前 3 后 3 位。
     */
    public static String maskSocialAccount(String account) {
        if (isBlank(account)) {
            return account;
        }
        String trimmed = account.trim();
        if (trimmed.length() <= 6) {
            return trimmed.charAt(0) + "****";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 3);
    }

    /**
     * 判断字符串是否为 null 或仅含空白。
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
