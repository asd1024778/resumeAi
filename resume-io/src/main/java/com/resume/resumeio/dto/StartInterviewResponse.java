package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 开始面试后，后端返回给前端的数据。
 * <p>
 * 包含新创建的会话信息与第一道题，前端据此进入答题界面。
 */
@Data
public class StartInterviewResponse {

    /** 新创建的面试会话信息（含会话 ID、状态等） */
    private InterviewSessionDto session;

    /** 本场面试的第一道题目 */
    private QuestionDto firstQuestion;
}
