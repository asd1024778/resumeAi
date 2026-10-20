package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 聊天请求体，用于 POST /api/chat/sync 同步聊天接口。
 */
@Data
public class ChatRequest {

    /** 用户向 AI 提出的问题或消息 */
    private String question;

    /** 会话 ID（可选），多轮对话时用于关联上下文历史 */
    private String sessionId;
}
