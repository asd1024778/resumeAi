package com.resume.resumeio.service;

import com.resume.resumeio.entity.DocumentChunk;
import com.resume.resumeio.rag.RagConstants;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 知识库核心服务。
 * <p>
 * 业务职责：串联「分块 → 向量化入库 → 相似度检索 → 上下文拼装」全流程，
 * 支持用户简历切块与全局/用户知识库两类数据源，供 AI 对话、简历分析、模拟面试等模块注入参考资料。
 */
@Service
public class RagKnowledgeService {

    /** Spring AI 向量库（SimpleVectorStore / Redis / PGVector 等，由配置决定） */
    private final VectorStore vectorStore;
    /** 长文本切分为 DocumentChunk 的服务 */
    private final DocumentChunkService chunkService;

    public RagKnowledgeService(VectorStore vectorStore, DocumentChunkService chunkService) {
        this.vectorStore = vectorStore;
        this.chunkService = chunkService;
    }

    /**
     * 将用户简历全文切块并写入向量库。
     * <p>
     * 每块携带 metadata：userId、resumeId、sourceType=resume、docId、chunkIndex，
     * 检索时可按用户与简历精确过滤。
     *
     * @param userId     用户 ID
     * @param resumeId   简历 ID
     * @param fullText   简历解析后的纯文本
     */
    public void indexResume(long userId, long resumeId, String fullText) {
        // 逻辑文档 ID，同一简历的所有块共享
        String docId = "resume-" + resumeId;
        // RAG 第一步：长文本切分为多个 Chunk
        List<DocumentChunk> chunks = chunkService.chunkText(fullText, docId);
        if (chunks.isEmpty()) {
            return;
        }
        // 每个 Chunk 转为 Spring AI Document，并附加检索过滤用的 metadata
        List<Document> documents = chunks.stream()
                .map(chunk -> toDocument(chunk, Map.of(
                        RagConstants.META_USER_ID, String.valueOf(userId),
                        RagConstants.META_RESUME_ID, String.valueOf(resumeId),
                        RagConstants.META_SOURCE_TYPE, RagConstants.SOURCE_RESUME,
                        RagConstants.META_DOC_ID, docId,
                        RagConstants.META_CHUNK_INDEX, String.valueOf(chunk.getChunkIndex())
                )))
                .toList();
        // 批量写入向量库（内部会调用 EmbeddingModel 向量化）
        vectorStore.add(documents);
    }

    /**
     * 将知识库文本（全局面试技巧或用户级资料）切块并写入向量库。
     *
     * @param docId    知识库文档 ID
     * @param title    可选标题，会拼在正文前一并切块
     * @param fullText 知识库正文
     * @param userId   所属用户（全局知识库可用 0）
     */
    public void indexKnowledge(String docId, String title, String fullText, long userId) {
        // 标题与正文合并，便于检索时匹配标题关键词
        String header = title != null ? title + "\n" : "";
        List<DocumentChunk> chunks = chunkService.chunkText(header + fullText, docId);
        if (chunks.isEmpty()) {
            return;
        }
        List<Document> documents = chunks.stream()
                .map(chunk -> toDocument(chunk, Map.of(
                        RagConstants.META_USER_ID, String.valueOf(userId),
                        RagConstants.META_SOURCE_TYPE, RagConstants.SOURCE_KNOWLEDGE,
                        RagConstants.META_DOC_ID, docId,
                        RagConstants.META_CHUNK_INDEX, String.valueOf(chunk.getChunkIndex())
                )))
                .toList();
        vectorStore.add(documents);
    }

    /**
     * 在指定用户的某份简历向量中做相似度检索。
     * <p>
     * 使用 filterExpression 限定：userId + resumeId + sourceType=resume。
     *
     * @param userId   用户 ID
     * @param resumeId 简历 ID
     * @param query    检索 query（会被 Embedding 后与库内向量比相似度）
     * @param topK     返回最相关的前 K 条
     * @return 相关 Document 列表，按相似度降序
     */
    public List<Document> searchResume(long userId, long resumeId, String query, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                // 元数据过滤：只搜该用户该简历的 resume 类型块
                .filterExpression(buildFilter(
                        RagConstants.META_USER_ID, String.valueOf(userId),
                        RagConstants.META_RESUME_ID, String.valueOf(resumeId),
                        RagConstants.META_SOURCE_TYPE, RagConstants.SOURCE_RESUME
                ))
                .build());
    }

    /**
     * 检索全局知识库（面试技巧、简历规范、评分标准等）。
     * <p>
     * 仅按 sourceType=knowledge 过滤，不限定 userId/resumeId。
     *
     * @param query 检索 query
     * @param topK  返回条数
     */
    public List<Document> searchKnowledge(String query, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(buildFilter(
                        RagConstants.META_SOURCE_TYPE, RagConstants.SOURCE_KNOWLEDGE
                ))
                .build());
    }

    /**
     * 混合检索：知识库 + 可选简历上下文，合并后截断为 topK 条。
     * <p>
     * 用于 AI 对话等场景：既参考通用知识，又结合用户当前简历。
     *
     * @param query    用户问题
     * @param userId   用户 ID
     * @param resumeId 简历 ID，为 null 则只搜知识库
     * @param topK     最终返回总条数上限
     */
    public List<Document> searchHybrid(String query, long userId, Long resumeId, int topK) {
        // 知识库取约一半配额，至少 2 条
        List<Document> merged = new ArrayList<>(searchKnowledge(query, Math.max(2, topK / 2)));
        if (resumeId != null) {
            // 简历侧同样取约一半配额
            merged.addAll(searchResume(userId, resumeId, query, Math.max(2, topK / 2)));
        }
        // 合并后可能超过 topK，截断
        return merged.stream().limit(topK).toList();
    }

    /**
     * 将检索到的 Document 列表格式化为可注入 Prompt 的【参考资料】文本块。
     *
     * @param documents RAG 检索结果
     * @return 多段「【参考】...」拼接的字符串；无结果时返回提示语
     */
    public String formatContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "（未检索到相关参考资料，请基于常识回答。）";
        }
        return documents.stream()
                .map(doc -> "【参考】" + doc.getText())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 将业务 Chunk 转为 Spring AI Document，并合并 metadata。
     */
    private Document toDocument(DocumentChunk chunk, Map<String, String> metadata) {
        Map<String, Object> meta = new HashMap<>(metadata);
        return new Document(chunk.getContent(), meta);
    }

    /**
     * 构建向量库 filterExpression 字符串。
     * <p>
     * 格式示例：{@code userId == '1' && resumeId == '2' && sourceType == 'resume'}
     * 键值成对传入：key1, value1, key2, value2, ...
     *
     * @param keyValues 交替的键名与值
     * @return Spring AI VectorStore 可解析的过滤表达式
     */
    private String buildFilter(String... keyValues) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(" && ");
            }
            sb.append(keyValues[i]).append(" == '").append(keyValues[i + 1]).append("'");
        }
        return sb.toString();
    }
}
