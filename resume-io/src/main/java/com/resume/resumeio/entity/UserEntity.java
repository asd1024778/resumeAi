package com.resume.resumeio.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账户实体，存储登录凭证与基础资料。
 * <p>
 * 敏感资料（姓名、电话等）不存于此表，由 Redis/内存单独管理。
 */
@Entity // 标记为 JPA 实体
@Table(name = "users") // 映射到 users 表
@Data
public class UserEntity {

    /** 用户主键，数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户名，全局唯一，最长 64 字符 */
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** 密码哈希值（非明文），由 BCrypt 等算法加密后存储，最长 128 字符 */
    @Column(nullable = false, length = 128)
    private String passwordHash;

    /** 用户邮箱，可选，最长 128 字符 */
    @Column(length = 128)
    private String email;

    /** 账户创建时间，插入后不可修改 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 账户最后更新时间，每次修改记录时自动刷新 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 实体首次持久化前的回调：同时设置 createdAt 与 updatedAt 为当前时间。
     */
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * 实体更新前的回调：刷新 updatedAt 为当前时间。
     */
    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
