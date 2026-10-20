package com.resume.resumeio.dto;

import com.resume.resumeio.entity.UserEntity;
import com.resume.resumeio.util.SensitiveDataMask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息 DTO，用于 API 响应中返回用户基础资料。
 * <p>
 * 提供 masked（脱敏）与 full（完整）两种构建方式，保护敏感信息。
 */
@Data
@NoArgsConstructor  // 无参构造，供 Jackson 反序列化使用
@AllArgsConstructor // 全参构造，便于测试与手动构建
public class UserDto {

    /** 用户唯一 ID */
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 用户邮箱（对外 API 默认脱敏显示） */
    private String email;

    /** 用户敏感资料（姓名、电话等，默认脱敏后返回） */
    private UserSensitiveProfileDto profile;

    /**
     * 构建脱敏版本的用户 DTO，用于对外 API 响应。
     * 邮箱与敏感资料均做掩码处理。
     *
     * @param entity  数据库用户实体
     * @param profile 敏感资料 DTO，可为 null
     * @return 脱敏后的 UserDto
     */
    public static UserDto masked(UserEntity entity, UserSensitiveProfileDto profile) {
        return build(entity, profile, true);
    }

    /**
     * 构建完整版本的用户 DTO，供用户本人查看自己的完整资料。
     *
     * @param entity  数据库用户实体
     * @param profile 敏感资料 DTO，可为 null
     * @return 未脱敏的 UserDto
     */
    public static UserDto full(UserEntity entity, UserSensitiveProfileDto profile) {
        return build(entity, profile, false);
    }

    /**
     * 兼容旧调用：仅返回基础字段，邮箱脱敏，不含敏感资料。
     *
     * @param entity 数据库用户实体
     * @return 脱敏后的 UserDto（profile 为 null）
     */
    public static UserDto from(UserEntity entity) {
        return masked(entity, null);
    }

    /**
     * 内部构建方法，根据 mask 参数决定是否对邮箱和敏感资料脱敏。
     */
    private static UserDto build(UserEntity entity, UserSensitiveProfileDto profile, boolean mask) {
        UserDto dto = new UserDto();
        dto.setId(entity.getId());
        dto.setUsername(entity.getUsername());
        // 根据 mask 标志决定是否对邮箱做掩码处理
        dto.setEmail(mask ? SensitiveDataMask.maskEmail(entity.getEmail()) : entity.getEmail());
        if (profile != null && !profile.isEmpty()) {
            // 根据 mask 标志决定返回脱敏或完整的敏感资料
            dto.setProfile(mask ? profile.masked() : profile);
        }
        return dto;
    }
}
