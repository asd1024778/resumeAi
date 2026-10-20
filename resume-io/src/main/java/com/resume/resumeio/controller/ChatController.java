package com.resume.resumeio.controller;

import com.resume.resumeio.dto.ApiResponse;
import com.resume.resumeio.dto.ChatRequest;
import com.resume.resumeio.security.SecurityUtils;
import com.resume.resumeio.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * RAG 增强的 AI 对话 API 控制器。
 * <p>
 * 职责：提供基于知识库检索增强生成（RAG）的 AI 问答能力，支持流式与同步两种响应模式。
 * 基础路径：{@code /api/chat}
 * </p>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** 对话业务服务，封装 RAG 检索与大模型调用 */
    private final ChatService chatService;

    /**
     * 构造器注入 ChatService。
     *
     * @param chatService 对话服务
     */
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 流式 AI 对话（Server-Sent Events）。
     * <p>
     * 路径：{@code GET /api/chat/stream?question=...}<br>
     * 权限：需登录<br>
     * 入参：{@code question}（查询参数，用户提问内容）<br>
     * 出参：{@code Flux<String>}，{@code text/event-stream} 格式，逐块推送 AI 回复文本
     * </p>
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam String question) {
        // 获取当前登录用户 ID，用于个性化 RAG 检索范围
        return SecurityUtils.currentUserId()
                // 将 userId 与 question 交给服务层，返回流式文本片段
                .flatMapMany(userId -> chatService.streamChat(question, userId));
    }

    /**
     * 同步 AI 对话（一次性返回完整回复）。
     * <p>
     * 路径：{@code POST /api/chat/sync}<br>
     * 权限：需登录<br>
     * 入参：{@link ChatRequest}（JSON 请求体，含 question 字段）<br>
     * 出参：{@code Mono<String>}，完整 AI 回复字符串
     * </p>
     */
    @PostMapping("/sync")
    public Mono<String> syncChat(@RequestBody ChatRequest request) {
        // 获取当前登录用户 ID
        return SecurityUtils.currentUserId()
                // 在弹性线程池执行阻塞式 LLM 调用，避免阻塞 Netty 线程
                .flatMap(userId -> Mono.fromCallable(() -> chatService.chat(request.getQuestion(), userId))
                        .subscribeOn(Schedulers.boundedElastic()));
    }
}
