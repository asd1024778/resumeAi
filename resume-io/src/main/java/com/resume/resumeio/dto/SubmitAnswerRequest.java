package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 提交面试答案时，前端发来的请求参数。
 * <p>
 * 对应 POST 提交答案接口的请求体。
 */
@Data
public class SubmitAnswerRequest {

    /** 当前面试会话 ID */
    private long sessionId;

    /** 正在回答的题目 ID */
    private long questionId;

    /** 用户对该题的回答内容 */
    private String answer;

    /** 是否已通过 AI 评价为合格；仅 true 时才会进入下一题 */
    private Boolean qualified;
}
