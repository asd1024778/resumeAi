package com.resume.resumeio.entity;

import lombok.Data;

import java.util.UUID;

/**
 * 文档分块实体：长文档被切成小段后，每一段的记录。
 * <p>
 * 用于知识库（RAG）场景——检索时找到最相关的几个块，再交给 AI 回答。
 * 此类暂未映射 JPA 表，作为内存/向量库中的数据结构使用。
 */
@Data // Lombok：自动生成 getter/setter 等样板代码
public class DocumentChunk {

    /** 块的唯一标识，创建时自动生成 UUID 字符串 */
    private String id = UUID.randomUUID().toString();

    /** 所属原始文档 ID，标识该块来自哪篇知识库文档 */
    private String documentId;

    /** 该分块的文本内容，是检索与问答的基本单元 */
    private String content;

    /** 分块序号，从 0 开始，表示这是原文的第几块 */
    private int chunkIndex;

    /** 该块在原文中的起始字符偏移量（可选，用于溯源定位） */
    private int startOffset;

    /** 该块在原文中的结束字符偏移量（可选，与 startOffset 配合定位原文位置） */
    private int endOffset;
}
