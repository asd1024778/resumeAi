package com.resume.resumeio.entity;

import com.resume.resumeio.dto.AnalysisReportDto;
import com.resume.resumeio.persistence.AnalysisReportConverter;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历 AI 分析报告持久化实体。
 * <p>
 * 完整报告内容以 JSON 形式存入 reportData 列，通过 AnalysisReportConverter 与 DTO 互转。
 */
@Entity // 标记为 JPA 实体
@Table(name = "analysis_reports", indexes = {
        @Index(name = "idx_reports_user_id", columnList = "userId"),   // 按用户查报告
        @Index(name = "idx_reports_resume_id", columnList = "resumeId") // 按简历查报告
})
@Data
public class AnalysisReportEntity {

    /**
     * 报告主键，与 DTO 中的 reportId 保持一致，
     * 便于前端沿用同一编号，无需额外映射。
     */
    @Id
    private Long id;

    /** 报告所属用户 ID，用于权限校验与列表筛选 */
    @Column(nullable = false)
    private Long userId;

    /** 关联的简历记录 ID，标识本次分析针对哪份上传简历 */
    @Column(nullable = false)
    private Long resumeId;

    /** 完整分析报告（评分、建议、雷达图等），序列化为 JSON 存入 TEXT 列 */
    @Convert(converter = AnalysisReportConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private AnalysisReportDto reportData;

    /** 报告创建时间，插入后不可修改 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 实体首次持久化前的回调：若 createdAt 未设置，则自动填充当前时间。
     */
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
