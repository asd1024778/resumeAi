package com.resume.resumeio.service.sensitive;

import com.resume.resumeio.dto.UserSensitiveProfileDto;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版用户敏感资料存储实现。
 * <p>
 * 业务职责：在 Redis 不可用或未配置时作为回退方案，将敏感资料保存在 JVM 堆内存中。
 * 使用 {@link ConcurrentHashMap} 保证多线程并发读写安全；
 * 进程重启后数据丢失，仅适用于开发/测试环境。
 */
public class InMemorySensitiveProfileStore implements SensitiveProfileStore {

    /** 线程安全的内存 Map：userId → 敏感资料 DTO */
    private final Map<Long, UserSensitiveProfileDto> store = new ConcurrentHashMap<>();

    /**
     * 按用户 ID 从内存 Map 读取敏感资料。
     */
    @Override
    public Optional<UserSensitiveProfileDto> findByUserId(Long userId) {
        // Optional.ofNullable：Map 中无 key 时返回 empty，有则包装 DTO
        return Optional.ofNullable(store.get(userId));
    }

    /**
     * 直接覆盖写入用户的敏感资料（全量替换）。
     */
    @Override
    public void save(Long userId, UserSensitiveProfileDto profile) {
        store.put(userId, profile);
    }

    /**
     * 原子合并更新：若 key 已存在则用新值替换旧值，避免并发读-改-写丢失字段。
     * <p>
     * 注意：当前 merge 函数为 (oldVal, newVal) -> newVal，即新值完全覆盖旧值；
     * 供需要原子写语义的场景调用（非接口方法，扩展用）。
     *
     * @param userId  用户主键
     * @param profile 新的敏感资料
     */
    public void mergeSave(Long userId, UserSensitiveProfileDto profile) {
        store.merge(userId, profile, (oldVal, newVal) -> newVal);
    }

    /**
     * 从内存 Map 移除指定用户的敏感资料。
     */
    @Override
    public void delete(Long userId) {
        store.remove(userId);
    }
}
