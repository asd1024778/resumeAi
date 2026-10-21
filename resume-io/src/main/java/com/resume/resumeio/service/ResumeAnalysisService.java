package com.resume.resumeio.service;

import com.resume.resumeio.dto.ResumeScore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 RAG 的简历 AI 分析服务。
 * <p>
 * 业务职责：对用户上传的简历进行多维度 AI 评分（技术能力、项目经验、沟通表达等）。
 * 流程：简历切块入库 → 多 query 检索相关片段 + 知识库 → 增强 Prompt → 大模型结构化输出 {@link ResumeScore}。
 * 采用 RAG 而非全文塞入 Prompt，避免超长简历撑爆上下文窗口。
 */
@Service
public class ResumeAnalysisService {

    /**
     * 多路检索 query 列表：从不同维度检索简历切块，覆盖技术、项目、教育、软实力等。
     * 每路 query 独立检索 topK=2，结果去重合并后注入 Prompt。
     */
    private static final List<String> ANALYSIS_QUERIES = List.of(
            "技术能力 编程语言 框架 工具",
            "项目经验 成果 职责 难点",
            "教育背景 学历 专业 证书",
            "沟通表达 团队协作 软实力"
    );

    /** 大模型客户端，支持 .entity() 将 JSON 响应映射为 Java 对象 */
    private final ChatClient chatClient;
    /** RAG 入库与检索服务 */
    private final RagKnowledgeService ragKnowledgeService;

    public ResumeAnalysisService(ChatClient chatClient, RagKnowledgeService ragKnowledgeService) {
        // 注入 AiConfig 统一构建的 ChatClient，避免每个 Service 各建一份客户端
        this.chatClient = chatClient;
        this.ragKnowledgeService = ragKnowledgeService;
    }

    /**
     * RAG 增强的简历分析主流程。
     *
     * @param userId     用户 ID
     * @param resumeId   简历 ID（与 reportId 一致）
     * @param resumeText 解析后的简历全文（用于入库与 Prompt 中摘要长度提示）
     * @return 结构化评分结果 ResumeScore
     */
    public ResumeScore analyze(long userId, long resumeId, String resumeText) {
        // RAG 第一步：将简历全文切块并向量入库，供后续 similaritySearch
        ragKnowledgeService.indexResume(userId, resumeId, resumeText);

        // LinkedHashSet：按插入顺序去重，避免多 query 检索到相同块重复占用上下文
        Set<Document> uniqueDocs = new LinkedHashSet<>();
        // 多维度 query：每路从该用户该简历中检索最相关的 2 个块
        for (String query : ANALYSIS_QUERIES) {
            uniqueDocs.addAll(ragKnowledgeService.searchResume(userId, resumeId, query, 2));
        }
        // 补充全局知识库：HR 评估标准、面试评分规范等
        uniqueDocs.addAll(ragKnowledgeService.searchKnowledge("简历评估 HR 面试 评分标准", 2));

        // 将去重后的 Document 格式化为【参考资料】文本块
        String context = ragKnowledgeService.formatContext(new ArrayList<>(uniqueDocs));
        // 构建发给大模型的 user Prompt：角色设定 + JSON 输出 schema + RAG 上下文
        String promptText = """
                你是一位资深HR和技术面试官。请结合【参考资料】与【简历片段】对候选人进行多维度评估。
                返回JSON格式，包含以下字段：
                - overallScore: 综合评分 (0-100)
                - dimensions: 各维度评分对象，键名包括：技术能力、项目经验、沟通表达、逻辑思维、基础知识
                - strengths: 优势列表 (字符串数组)
                - weaknesses: 不足列表 (字符串数组)
                - suggestions: 改进建议列表 (字符串数组)

                【参考资料】
                """ + context + """

                【简历片段（RAG 检索）】
                已包含在上述参考中；若信息不足可结合常识合理推断，但不得编造经历。

                请基于候选人简历内容评分，简历全文摘要长度约 """ + resumeText.length() + " 字。\n";

        // AI 调用：同步请求，响应 JSON 自动反序列化为 ResumeScore
        return chatClient.prompt()
                .user(promptText)
                .call()
                .entity(ResumeScore.class);
    }
}
