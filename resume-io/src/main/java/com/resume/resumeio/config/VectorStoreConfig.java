/*
 * 包声明：Spring 配置类所在包。
 */
package com.resume.resumeio.config;

/*
 * 导入说明：
 * - LocalEmbeddingModel：项目自定义的本地文本向量化实现
 * - EmbeddingModel / VectorStore / SimpleVectorStore：Spring AI 向量检索抽象与内存实现
 * - ConditionalOnMissingBean：容器中尚无 EmbeddingModel 时才注册默认 Bean
 */
import com.resume.resumeio.rag.LocalEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 向量存储配置。
 * <p>
 * 使用内存版 {@link SimpleVectorStore} 配合本地 {@link LocalEmbeddingModel}，
 * 无需外部向量数据库即可运行知识检索。
 */
@Configuration
public class VectorStoreConfig {

    /**
     * 注册默认 Embedding 模型（若用户未自定义 EmbeddingModel Bean）。
     *
     * @return 本地向量化模型实例
     */
    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel embeddingModel() {
        return new LocalEmbeddingModel();
    }

    /**
     * 创建内存向量库，依赖 EmbeddingModel 将文本转为向量后存储与检索。
     *
     * @param embeddingModel 文本向量化模型
     * @return 可用于 RAG 检索的 VectorStore
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
