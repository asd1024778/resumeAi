package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 提交面试答案后，后端返回给前端的数据。
 * <p>
 * 包含用户刚提交的消息记录，以及下一道题（若已全部答完则为 null）。
 */
@Data
public class SubmitAnswerResponse {

    /** 用户刚才提交的那条回答消息（含 role、content 等） */
    private MessageDto message;

    /** 下一道题目；当前题评价合格且尚有后续题目时返回 */
    private QuestionDto nextQuestion;

    /** 本次评价是否合格（由前端在流式评价结束后携带提交） */
    private Boolean qualified;

    /** 是否已完成全部题目（最后一题合格时为 true） */
    private Boolean interviewCompleted;
}
