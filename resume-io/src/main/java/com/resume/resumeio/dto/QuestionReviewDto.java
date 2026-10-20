package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 面试逐题复盘条目 DTO。
 * <p>
 * 记录每道题的问题、用户回答、AI 评分与点评，用于报告中的复盘模块。
 */
@Data
public class QuestionReviewDto {

    /** 面试题目文本 */
    private String question;

    /** 用户对该题的回答内容 */
    private String myAnswer;

    /** AI 对该题回答的评分，范围 0~100 */
    private int aiScore;

    /** AI 对该题回答的文字点评 */
    private String aiComment;
}
