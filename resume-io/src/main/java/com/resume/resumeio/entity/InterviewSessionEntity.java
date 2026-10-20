package com.resume.resumeio.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟面试会话实体，记录一场面试的整体状态与进度。
 * <p>
 * 使用 JPA 乐观锁（@Version）防止并发提交答案时的写冲突。
 */
@Entity // 标记为 JPA 实体
@Table(name = "interview_sessions", indexes = {
        @Index(name = "idx_interview_user_id", columnList = "userId") // 按用户查会话列表
})
@Data
public class InterviewSessionEntity {

    /** 会话主键，数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发起面试的用户 ID */
    @Column(nullable = false)
    private Long userId;

    /** 关联的分析报告 ID（前端字段名 resumeId，实际指向 analysis_reports 表） */
    @Column(nullable = false)
    private Long reportId;

    /** 会话状态：in_progress（进行中）或 completed（已结束），最长 32 字符 */
    @Column(nullable = false, length = 32)
    private String status;

    /** 当前答题进度索引，指向正在回答或下一道题的下标（从 0 开始） */
    @Column(nullable = false)
    private int currentIndex;

    /** JPA 乐观锁版本号，并发更新时若版本不匹配则抛出 OptimisticLockException */
    @Version
    private Long version;

    /** 会话创建时间，插入后不可修改 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 会话最后更新时间，每次状态变更时自动刷新 */
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
