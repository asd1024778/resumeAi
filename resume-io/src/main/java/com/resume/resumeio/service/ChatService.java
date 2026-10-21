package com.resume.resumeio.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 基于 RAG 的 AI 对话服务。
 * <p>
 * 业务职责：接收用户问题 → 混合检索知识库与简历片段 → 拼装增强 Prompt →
 * 调用大模型生成回答（支持同步与流式两种模式），供通用聊天与面试反馈等场景复用。
 */
@Service
public class ChatService {

    /** Spring AI ChatClient，封装大模型调用（同步 call / 流式 stream） */
    private final ChatClient chatClient;
    /** RAG 检索与上下文格式化服务 */
    private final RagKnowledgeService ragKnowledgeService;

    public ChatService(ChatClient chatClient, RagKnowledgeService ragKnowledgeService) {
        // 注入 AiConfig 统一构建的 ChatClient，模型/地址/密钥等配置只在那一处生效
        this.chatClient = chatClient;
        this.ragKnowledgeService = ragKnowledgeService;
    }

    /**
     * 流式 RAG 对话：检索参考资料后以 SSE/Flux 逐 token 返回回答。
     *
     * @param question 用户问题
     * @param userId   当前用户 ID
     * @return 字符串片段流，前端可实时展示
     */
    public Flux<String> streamChat(String question, long userId) {
        // 构建带 RAG 上下文的完整 Prompt（不含简历 ID，仅知识库）
        String prompt = buildRagPrompt(question, userId, null);
        // stream().content()：Reactive 流式输出模型生成文本
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }

    /**
     * 同步 RAG 对话：等待模型完整响应后一次性返回。
     *
     * @param question 用户问题
     * @param userId   用户 ID
     * @return 完整回答文本
     */
    public String chat(String question, long userId) {
        String prompt = buildRagPrompt(question, userId, null);
        // call().content()：阻塞直到模型返回全文
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    /**
     * 带简历上下文的 RAG 流式生成（供模拟面试反馈等场景复用）。
     * <p>
     * 在标准 RAG Prompt 前可追加 extraInstruction（如面试官角色与字数限制）。
     *
     * @param question          用于检索的 query（可与展示给模型的「问题」不同）
     * @param userId            用户 ID
     * @param resumeId          关联简历 ID，null 则不做简历检索
     * @param extraInstruction  额外系统指令，可为 null
     * @return 流式文本片段
     */
    public Flux<String> streamChatWithResumeContext(
            String question, long userId, Long resumeId, String extraInstruction
    ) {
        // 混合检索：知识库 + 该用户该简历的切块
        String prompt = buildRagPrompt(question, userId, resumeId);
        // 面试反馈等场景：将角色指令置于 Prompt 最前
        if (extraInstruction != null && !extraInstruction.isBlank()) {
            prompt = extraInstruction + "\n\n" + prompt;
        }
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }

    /**
     * 带简历上下文的 RAG 同步生成（供 AI 动态出题等场景复用）。
     */
    public String chatWithResumeContext(
            String question, long userId, Long resumeId, String extraInstruction
    ) {
        String prompt = buildRagPrompt(question, userId, resumeId);
        if (extraInstruction != null && !extraInstruction.isBlank()) {
            prompt = extraInstruction + "\n\n" + prompt;
        }
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    /**
     * RAG Prompt 构建：检索 → 格式化上下文 → 拼接用户问题。
     *
     * @param question 检索 query 与最终展示的用户问题
     * @param userId   用户 ID
     * @param resumeId 简历 ID，null 时 searchHybrid 只查知识库
     * @return 可发送给大模型的完整 user 消息
     */
    private String buildRagPrompt(String question, long userId, Long resumeId) {
        // topK=5：最多取 5 段参考资料注入上下文
        var docs = ragKnowledgeService.searchHybrid(question, userId, resumeId, 5);
        // 转为「【参考】...」多段文本
        String context = ragKnowledgeService.formatContext(docs);
        return """
                你是 AI 简历与面试助手。请优先依据【参考资料】回答；资料不足时可补充通用建议，并说明依据不足。
                回答简洁、专业、可操作。

                【参考资料】
                """ + context + """

                【用户问题】
                """ + question;
    }
}
