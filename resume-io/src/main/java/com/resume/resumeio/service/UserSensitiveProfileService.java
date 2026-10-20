package com.resume.resumeio.service;

import com.resume.resumeio.dto.UpdateUserProfileRequest;
import com.resume.resumeio.dto.UserSensitiveProfileDto;
import com.resume.resumeio.service.sensitive.SensitiveProfileStore;
import org.springframework.stereotype.Service;

/**
 * 用户敏感资料业务服务。
 * <p>
 * 业务职责：封装敏感信息（真实姓名、手机、身份证等）的查询、脱敏展示与更新，
 * 委托 {@link SensitiveProfileStore} 完成实际存储（Redis 或内存），
 * 与 {@link UserService} 协同完成注册与个人资料维护。
 */
@Service
public class UserSensitiveProfileService {

    /** 敏感资料存储后端（Redis / 内存，由 Spring 配置决定注入哪个实现） */
    private final SensitiveProfileStore sensitiveProfileStore;

    public UserSensitiveProfileService(SensitiveProfileStore sensitiveProfileStore) {
        this.sensitiveProfileStore = sensitiveProfileStore;
    }

    /**
     * 获取用户完整敏感资料；若从未保存过则返回空 DTO 而非 null。
     *
     * @param userId 用户主键
     * @return 敏感资料 DTO（可能各字段为空）
     */
    public UserSensitiveProfileDto getProfile(Long userId) {
        // orElseGet：Redis/内存中无记录时 new 一个空对象，避免 NPE
        return sensitiveProfileStore.findByUserId(userId).orElseGet(UserSensitiveProfileDto::new);
    }

    /**
     * 获取脱敏后的敏感资料，供普通 API 响应使用（如手机号中间四位打星）。
     *
     * @param userId 用户主键
     * @return 脱敏 DTO；若资料为空则原样返回空 DTO
     */
    public UserSensitiveProfileDto getMaskedProfile(Long userId) {
        UserSensitiveProfileDto profile = getProfile(userId);
        // 空资料无需脱敏；非空则调用 DTO 内置 masked() 方法
        return profile.isEmpty() ? profile : profile.masked();
    }

    /**
     * 根据更新请求全量覆盖用户的敏感资料字段。
     *
     * @param userId  用户主键
     * @param request 前端提交的各敏感字段
     * @return 保存后的完整 DTO（未脱敏）
     */
    public UserSensitiveProfileDto updateProfile(Long userId, UpdateUserProfileRequest request) {
        // 将请求字段 trim 后组装为 DTO，空串转为 null 便于存储层判断
        UserSensitiveProfileDto profile = UserSensitiveProfileDto.builder()
                .realName(trimToNull(request.getRealName()))
                .phone(trimToNull(request.getPhone()))
                .idCard(trimToNull(request.getIdCard()))
                .address(trimToNull(request.getAddress()))
                .socialAccount(trimToNull(request.getSocialAccount()))
                .build();
        // 写入 Redis 或内存
        sensitiveProfileStore.save(userId, profile);
        return profile;
    }

    /**
     * 注册等场景下首次保存敏感资料；若 profile 为空则跳过写入。
     *
     * @param userId  新注册用户 ID
     * @param profile 注册时一并提交的敏感信息
     */
    public void saveProfile(Long userId, UserSensitiveProfileDto profile) {
        if (profile != null && !profile.isEmpty()) {
            sensitiveProfileStore.save(userId, profile);
        }
    }

    /**
     * 字符串 trim 工具：null 保持 null，空白串转为 null，否则返回 trim 结果。
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
