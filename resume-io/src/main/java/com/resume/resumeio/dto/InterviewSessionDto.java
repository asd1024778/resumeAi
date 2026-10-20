package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 一场模拟面试的会话信息 DTO，用于前后端传递面试状态。
 */
@Data
public class InterviewSessionDto {

    /** 会话唯一 ID，由后端创建后返回给前端 */
    private long id;

    /** 发起面试的用户 ID，通常从 JWT 令牌中解析 */
    private long userId;

    /** 关联的分析报告 ID（前端字段名 resumeId，实际指向 analysis_reports 表） */
    private long resumeId;

    /** 会话状态：in_progress（进行中）或 completed（已结束） */
    private String status;
}
