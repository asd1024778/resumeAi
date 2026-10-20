package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 请求 AI 流式点评时，前端发来的参数。
 * <p>
 * 对应 POST /api/interview/stream 接口，后端以 SSE 流式返回 AI 点评内容。
 */
@Data
public class InterviewStreamRequest {

    /** 当前面试会话 ID */
    private long sessionId;

    /** 正在点评的题目 ID */
    private long questionId;

    /** 用户对该题的回答内容，AI 将基于此生成流式点评 */
    private String answer;
}
