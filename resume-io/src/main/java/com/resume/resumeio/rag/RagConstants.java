package com.resume.resumeio.rag;

/**
 * RAG 向量库元数据常量定义。
 * <p>
 * 业务职责：统一管理写入向量库（VectorStore）时 Document 元数据的字段名与取值，
 * 供 {@link com.resume.resumeio.service.RagKnowledgeService} 在入库、检索过滤时使用，
 * 确保「按用户 / 按简历 / 按来源类型」的隔离检索语义一致。
 */
public final class RagConstants {

    /** 私有构造器，禁止实例化工具类 */
    private RagConstants() {
    }

    /** 元数据：所属用户 ID，用于多租户隔离检索 */
    public static final String META_USER_ID = "userId";
    /** 元数据：关联的简历 ID，用于只检索某份简历的切块 */
    public static final String META_RESUME_ID = "resumeId";
    /** 元数据：文档来源类型（resume / knowledge / interview） */
    public static final String META_SOURCE_TYPE = "sourceType";
    /** 元数据：逻辑文档 ID，同一文档的多块共享此 ID */
    public static final String META_DOC_ID = "docId";
    /** 元数据：块在原文档中的序号，便于溯源与排序 */
    public static final String META_CHUNK_INDEX = "chunkIndex";

    /** 来源类型：用户上传的简历切块 */
    public static final String SOURCE_RESUME = "resume";
    /** 来源类型：全局或用户级知识库（面试技巧、评分标准等） */
    public static final String SOURCE_KNOWLEDGE = "knowledge";
    /** 来源类型：面试相关上下文（预留，供后续扩展） */
    public static final String SOURCE_INTERVIEW = "interview";

    /**
     * 全局知识库使用的虚拟用户 ID。
     * 入库时将 userId 设为 "0"，检索知识库时可不按真实用户过滤。
     */
    public static final String GLOBAL_USER_ID = "0";
}
