package com.resume.resumeio.controller;

import com.resume.resumeio.dto.ApiResponse;
import com.resume.resumeio.security.SecurityUtils;
import com.resume.resumeio.service.DocumentParserService;
import com.resume.resumeio.service.RagKnowledgeService;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

/**
 * 知识库管理 API 控制器。
 * <p>
 * 职责：接收用户上传的文档，解析文本、分块并向量化入库，作为 RAG 检索的数据源。
 * 基础路径：{@code /api/knowledge}
 * </p>
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    /** 文档解析服务，将 PDF/Word 等二进制文件转为纯文本 */
    private final DocumentParserService parserService;
    /** RAG 知识库服务，负责文本分块、向量嵌入与索引存储 */
    private final RagKnowledgeService ragKnowledgeService;

    /**
     * 构造器注入依赖。
     *
     * @param parserService        文档解析服务
     * @param ragKnowledgeService  RAG 知识库服务
     */
    public KnowledgeController(DocumentParserService parserService, RagKnowledgeService ragKnowledgeService) {
        this.parserService = parserService;
        this.ragKnowledgeService = ragKnowledgeService;
    }

    /**
     * 上传知识库文档并向量化入库。
     * <p>
     * 路径：{@code POST /api/knowledge/upload}<br>
     * 权限：需登录<br>
     * 入参：multipart/form-data — {@code file}（必填，文档文件）、{@code title}（可选，文档标题）<br>
     * 出参：{@link ApiResponse}{@code <String>}，成功时返回 docId 提示；失败时返回错误消息
     * </p>
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApiResponse<String>> uploadKnowledge(
            @RequestPart("file") FilePart file,
            @RequestPart(value = "title", required = false) String title
    ) {
        // 获取当前登录用户 ID，知识库按用户隔离
        return SecurityUtils.currentUserId()
                // 将 FilePart 的多个 DataBuffer 合并为单个缓冲区
                .flatMap(userId -> DataBufferUtils.join(file.content())
                        .flatMap(buffer -> Mono.fromCallable(() -> {
                                    // 分配字节数组并读取缓冲区全部内容
                                    byte[] bytes = new byte[buffer.readableByteCount()];
                                    buffer.read(bytes);
                                    // 释放 Netty 缓冲区，防止内存泄漏
                                    DataBufferUtils.release(buffer);
                                    // 生成全局唯一文档 ID
                                    String docId = "knowledge-" + UUID.randomUUID();
                                    // 解析文件二进制内容为纯文本
                                    String text = parserService.parse(new ByteArrayInputStream(bytes));
                                    // 优先使用用户提供的标题，否则回退为原始文件名
                                    String docTitle = title != null ? title : file.filename();
                                    // 分块、向量化并写入向量数据库，关联 userId
                                    ragKnowledgeService.indexKnowledge(docId, docTitle, text, userId);
                                    // 返回成功消息及 docId
                                    return ApiResponse.success("知识库文档已入库，docId=" + docId);
                                })
                                // 解析与索引为阻塞操作，切换到弹性线程池
                                .subscribeOn(Schedulers.boundedElastic())
                                // 捕获任意异常，转换为友好错误响应
                                .onErrorResume(e -> Mono.just(
                                        ApiResponse.error("知识库上传失败: " + e.getMessage())
                                ))));
    }
}
