package com.resume.resumeio.service.sensitive;

import com.resume.resumeio.dto.UserSensitiveProfileDto;

import java.util.Optional;

/**
 * 用户敏感资料存储抽象接口。
 * <p>
 * 业务职责：定义敏感信息（真实姓名、手机、身份证、地址等）的读写契约，
 * 与关系型数据库中的账号信息分离存储，便于选用 Redis 或内存等不同后端。
 * 实现类：{@link RedisSensitiveProfileStore}、{@link InMemorySensitiveProfileStore}。
 */
public interface SensitiveProfileStore {

    /**
     * 按用户 ID 查询敏感资料。
     *
     * @param userId 用户主键
     * @return 存在则返回 DTO，不存在则 empty
     */
    Optional<UserSensitiveProfileDto> findByUserId(Long userId);

    /**
     * 保存或覆盖用户的完整敏感资料。
     *
     * @param userId  用户主键
     * @param profile 敏感资料 DTO
     */
    void save(Long userId, UserSensitiveProfileDto profile);

    /**
     * 删除指定用户的敏感资料（如注销账号时）。
     *
     * @param userId 用户主键
     */
    void delete(Long userId);
}
