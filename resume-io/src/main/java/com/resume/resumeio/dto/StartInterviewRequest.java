package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 开始模拟面试时，前端发来的请求参数。
 * <p>
 * 对应 POST 开始面试接口的请求体。
 */
@Data
public class StartInterviewRequest {

    /** 要基于哪份分析报告开始面试（前端字段名 resumeId，实际为 reportId） */
    private long resumeId;
}
