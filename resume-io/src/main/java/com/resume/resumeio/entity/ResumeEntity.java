package com.resume.resumeio.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户上传的简历记录实体。
 * <p>
 * 保存原始文件名与解析后的纯文本，供 AI 分析与面试出题使用。
 */
@Entity // 标记为 JPA 实体
@Table(name = "resumes", indexes = {
        @Index(name = "idx_resumes_user_id", columnList = "userId") // 按用户查简历列表
})
@Data
public class ResumeEntity {

    /** 简历记录主键，数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 上传该简历的用户 ID */
    @Column(nullable = false)
    private Long userId;

    /** 原始上传文件名（含扩展名），最长 256 字符 */
    @Column(nullable = false, length = 256)
    private String filename;

    /** 简历解析后的纯文本内容，用于 AI 读取与分析 */
    @Lob // 大对象字段
    @Column(columnDefinition = "TEXT")
    private String parsedText;

    /** 上传时间，插入后不可修改 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 实体首次持久化前的回调：自动填充 createdAt 为当前时间。
     */
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
