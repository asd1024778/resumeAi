package com.resume.resumeio.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分析报告列表摘要，供个人中心展示，避免返回完整 JSON。
 */
@Data
public class ReportSummaryDto {

    /** 报告 ID（与 resumeId 相同） */
    private long reportId;

    /** 简历标题 */
    private String resumeTitle;

    /** 目标岗位（可选） */
    private String jobTitle;

    /** 综合评分 0~100 */
    private int score;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
