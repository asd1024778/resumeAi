package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 一条对话消息的数据传输对象，用于面试过程中的问答交互。
 * <p>
 * 可以是用户提交的回答，也可以是 AI 返回的点评或下一道问题提示。
 */
@Data // Lombok：自动生成 getter/setter 等
public class MessageDto {

    /** 所属面试会话 ID，标识这条消息属于哪场面试 */
    private long sessionId;

    /** 消息发送者角色：user（用户）或 assistant（AI 助手） */
    private String role;

    /** 消息的文本内容（用户回答或 AI 回复） */
    private String content;
}
