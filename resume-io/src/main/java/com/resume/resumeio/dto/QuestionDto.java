package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 一道面试题目的完整信息 DTO，用于前后端传递题目详情。
 */
@Data
public class QuestionDto {

    /** 题目唯一 ID */
    private long id;

    /** 所属面试会话 ID */
    private long sessionId;

    /** 题目内容（面试问题的完整文本） */
    private String content;

    /** 题目分类，如"综合"、"Java"、"算法" */
    private String category;

    /** 难度等级：easy / medium / hard */
    private String difficulty;

    /** 题目在本次会话中的顺序号，从 1 开始 */
    private int order;
}
