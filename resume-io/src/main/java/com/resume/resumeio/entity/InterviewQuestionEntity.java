package com.resume.resumeio.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 面试题目实体类，与面试会话（InterviewSessionEntity）为一对多关系。
 * <p>
 * 一场模拟面试包含多道题目，每道题按 questionOrder 顺序依次展示给候选人。
 */
@Entity // 标记为 JPA 实体，映射到数据库表
@Table(name = "interview_questions", indexes = {
        // 为 sessionId 建索引，加速按会话查询题目列表
        @Index(name = "idx_question_session_id", columnList = "sessionId")
})
@Data // Lombok：自动生成 getter/setter、equals、hashCode、toString
public class InterviewQuestionEntity {

    /** 题目主键，数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属面试会话 ID，关联 interview_sessions 表 */
    @Column(nullable = false)
    private Long sessionId;

    /** 题目正文（面试问题文本），使用 TEXT 类型存储长文本 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 题目分类，如"综合"、"Java"、"算法"等，最长 64 字符 */
    @Column(length = 64)
    private String category;

    /** 难度等级：easy / medium / hard，最长 16 字符 */
    @Column(length = 16)
    private String difficulty;

    /** 题目在本次会话中的顺序号，从 1 开始递增，用于控制出题顺序 */
    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    /** 候选人本轮最终合格回答（不合格重答时不覆盖，合格时写入） */
    @Column(columnDefinition = "TEXT")
    private String userAnswer;

    /** 本题是否已通过 AI 评价为合格 */
    private Boolean passed;
}
