/*
 * 包声明：通用工具类所在包。
 */
package com.resume.resumeio.util;

/**
 * 密钥脱敏工具。
 * <p>
 * 项目中所有「密钥/令牌」出现在日志、启动横幅、错误提示里时都必须先经过本类，
 * 避免 API Key 以明文形式落到日志文件、容器日志、CI 输出或截图里。
 * <p>
 * 脱敏规则：保留前 6 位与后 4 位，中间固定为 {@code ****}。
 * 例：{@code sk-1234567890abcdef1234567890abcdef} → {@code sk-1234****cdef}
 */
public final class SecretMask {

    /** 工具类禁止实例化 */
    private SecretMask() {
    }

    /**
     * 脱敏任意密钥字符串。
     *
     * @param secret 原始密钥，可为 null/空白
     * @return 脱敏后的字符串；未配置时返回「(未配置)」
     */
    public static String mask(String secret) {
        if (secret == null || secret.isBlank()) {
            return "(未配置)";
        }
        String trimmed = secret.trim();
        // 太短的串不泄露任何字符
        if (trimmed.length() <= 10) {
            return "****";
        }
        return trimmed.substring(0, 6) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * 判断密钥是否为空（未配置）。
     *
     * @param secret 原始密钥
     * @return true 表示未配置或仅含空白
     */
    public static boolean isBlank(String secret) {
        return secret == null || secret.isBlank();
    }
}
