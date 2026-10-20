package com.resume.resumeio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 通用聊天响应体，包含 AI 的完整回答文本。
 * <p>
 * 用于同步聊天接口（POST /api/chat/sync）的响应。
 */
@Data
@AllArgsConstructor // 全参构造，便于一行代码构建响应
public class ChatResponse {

    /** AI 生成的回答内容 */
    private String content;
}
