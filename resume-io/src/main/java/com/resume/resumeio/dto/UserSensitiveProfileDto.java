package com.resume.resumeio.dto;

import com.resume.resumeio.util.SensitiveDataMask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户敏感资料 DTO，包含姓名、电话、身份证、地址、社交账号等。
 * <p>
 * 敏感资料不存入 users 表，由 Redis/内存单独管理；对外 API 默认脱敏返回。
 */
@Data
@Builder        // 支持 Builder 模式构建对象
@NoArgsConstructor
@AllArgsConstructor
public class UserSensitiveProfileDto {

    /** 真实姓名 */
    private String realName;

    /** 手机号码 */
    private String phone;

    /** 身份证号码 */
    private String idCard;

    /** 联系地址 */
    private String address;

    /** 社交账号（微信、QQ 等） */
    private String socialAccount;

    /**
     * 生成脱敏后的副本，用于对外 API 响应。
     * 各字段按规则做掩码处理（如手机号中间四位隐藏）。
     *
     * @return 脱敏后的 UserSensitiveProfileDto 新实例
     */
    public UserSensitiveProfileDto masked() {
        return UserSensitiveProfileDto.builder()
                .realName(SensitiveDataMask.maskName(realName))
                .phone(SensitiveDataMask.maskPhone(phone))
                .idCard(SensitiveDataMask.maskIdCard(idCard))
                .address(SensitiveDataMask.maskAddress(address))
                .socialAccount(SensitiveDataMask.maskSocialAccount(socialAccount))
                .build();
    }

    /**
     * 判断所有敏感字段是否均为空。
     *
     * @return true 表示没有任何有效敏感资料
     */
    public boolean isEmpty() {
        return isBlank(realName)
                && isBlank(phone)
                && isBlank(idCard)
                && isBlank(address)
                && isBlank(socialAccount);
    }

    /** 判断字符串是否为 null 或空白 */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
