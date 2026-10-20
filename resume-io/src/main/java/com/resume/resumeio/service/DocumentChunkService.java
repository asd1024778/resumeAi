package com.resume.resumeio.service;

import com.resume.resumeio.entity.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档分块服务：RAG 流水线的前置步骤。
 * <p>
 * 业务职责：将长文档（简历全文、知识库文章）切分为多个较小文本块（Chunk），
 * 以便向量化入库并在检索时只取与问题最相关的片段，避免超出大模型上下文长度限制。
 * <p>
 * 使用 Spring AI 的 {@link TokenTextSplitter}，在中文标点处优先断开以保持语义完整。
 */
@Service
@Slf4j
public class DocumentChunkService {

    /** Tika 实例，用于从上传文件中提取纯文本 */
    private final Tika tika = new Tika();

    /**
     * 文本分割器：按 token 数量切分，并在中文标点处优先断开。
     * <p>
     * 参数说明：
     * <ul>
     *   <li>500 — 每块最多约 500 token（约 750 个中文字）</li>
     *   <li>100 — 小于 100 字符的块会合并到相邻块</li>
     *   <li>10 — 小于 10 字符的碎片直接丢弃</li>
     *   <li>1000 — 单篇文档最多切 1000 块，防止极端长文 OOM</li>
     *   <li>true — 切分时保留分隔符（标点）</li>
     *   <li>标点列表 — 优先在这些字符处切分，减少句中断开</li>
     * </ul>
     */
    TokenTextSplitter splitter = new TokenTextSplitter(
            500,    // 每块最多 500 个 token（约 750 个中文字）
            100,    // 小于 100 字符的块会被合并到相邻块
            10,     // 小于 10 字符的碎片直接丢弃
            1000,   // 单篇文档最多切 1000 块
            true,   // 保留分隔符（标点符号）
            List.of('。', '？', '！', '；', '、', '：', ' ', '\n') // 优先在这些标点处切分
    );

    /**
     * 将纯文本分割为多个 {@link DocumentChunk}（供 RAG 向量入库使用）。
     *
     * @param fullText   完整文本（如简历 parsedText）
     * @param documentId 逻辑文档 ID，如 resume-123，多块共享同一 ID
     * @return 分块列表；空文本返回空列表
     */
    public List<DocumentChunk> chunkText(String fullText, String documentId) {
        // 空内容无需切块
        if (fullText == null || fullText.isBlank()) {
            return List.of();
        }
        // 包装为 Spring AI Document，供 splitter 处理
        Document sourceDoc = new Document(fullText);
        // apply：按 token 与标点规则切分为多个 Document 片段
        List<Document> chunks = splitter.apply(List.of(sourceDoc));

        // 将 Spring AI Document 转为业务实体 DocumentChunk
        List<DocumentChunk> chunkList = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            DocumentChunk entity = new DocumentChunk();
            entity.setDocumentId(documentId);
            entity.setContent(chunk.getText());
            entity.setChunkIndex(i);  // 从 0 起递增，写入向量库 metadata
            chunkList.add(entity);
        }
        log.info("文本分块完成 documentId={} 块数={}", documentId, chunkList.size());
        return chunkList;
    }

    /**
     * 解析上传的文件并分割成多个文本块（文件上传 + 知识库导入场景）。
     *
     * @param file       上传的 MultipartFile
     * @param documentId 文档唯一标识，便于溯源「这段文字来自哪个文件」
     * @return 分块列表
     */
    public List<DocumentChunk> parseAndChunk(MultipartFile file, String documentId) throws IOException, TikaException {
        // Tika 从流中提取全文
        String fullText = tika.parseToString(file.getInputStream());
        log.info("提取文本长度：{}字符", fullText.length());

        // Spring AI Document 包装纯文本
        Document sourceDoc = new Document(fullText);
        // Token 级切分
        List<Document> chunks = splitter.apply(List.of(sourceDoc));

        List<DocumentChunk> chunkList = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            DocumentChunk entity = new DocumentChunk();
            entity.setDocumentId(documentId);
            entity.setContent(chunk.getText());
            entity.setChunkIndex(i);
            chunkList.add(entity);
        }

        log.info("文档分块完成，共生成 {} 个块", chunkList.size());
        return chunkList;
    }
}
