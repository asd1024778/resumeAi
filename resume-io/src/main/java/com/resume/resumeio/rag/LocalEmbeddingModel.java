package com.resume.resumeio.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 本地轻量级文本向量化（Embedding）实现。
 * <p>
 * 业务职责：将文本转换为固定维度的浮点向量，供向量库做相似度检索。
 * 无需调用外部 Embedding API，适合本地开发与测试；
 * 生产环境可替换为 DashScope、OpenAI 等云端 Embedding 模型。
 * <p>
 * 算法：基于词/字符哈希的稀疏向量 + L2 归一化，非语义级深度模型。
 */
public class LocalEmbeddingModel implements EmbeddingModel {

    /** 输出向量维度，与向量库配置需保持一致 */
    static final int DIMENSION = 384;

    /**
     * Spring AI 批量 Embedding 入口：对请求中的每条文本生成向量。
     *
     * @param request 包含待向量化的文本列表（instructions）
     * @return 与输入一一对应的 Embedding 列表
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        // 存放每条文本对应的向量结果
        List<Embedding> embeddings = new ArrayList<>();
        // 从请求中取出待向量化的文本列表
        List<String> instructions = request.getInstructions();
        // 逐条文本计算向量，index 用于与输入顺序对齐
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(embed(instructions.get(i)), i));
        }
        // 封装为 Spring AI 标准响应对象
        return new EmbeddingResponse(embeddings);
    }

    /**
     * 对 Spring AI Document 对象做向量化（委托给字符串 embed 方法）。
     */
    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    /**
     * 核心向量化逻辑：将文本映射为 DIMENSION 维单位向量。
     * <p>
     * 步骤：小写归一化 → 按空格分词累加哈希桶 → 中文字符逐字累加 → L2 归一化 → 空向量兜底。
     *
     * @param text 原始文本
     * @return 长度为 DIMENSION 的浮点数组
     */
    public float[] embed(String text) {
        // 初始化全零向量
        float[] vector = new float[DIMENSION];
        // 空文本：用第 0 维置 1 作为占位，避免零向量无法参与相似度计算
        if (text == null || text.isBlank()) {
            vector[0] = 1.0f;
            return vector;
        }
        // 统一小写，减少大小写带来的哈希差异
        String normalized = text.toLowerCase(Locale.ROOT);
        // 英文等有空格分隔的文本：按空白切分为 token
        for (String token : normalized.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            // 将 token 哈希映射到向量桶并累加权重
            addToken(vector, token);
        }
        // 中文等无空格文本：按字符补充特征，避免整段中文只产生极少 token
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            addToken(vector, String.valueOf(c));
        }
        // L2 归一化，使向量模长为 1，便于余弦相似度计算
        normalize(vector);
        // 若归一化后仍为零向量（极端情况），再次用第 0 维兜底
        if (vector[0] == 0 && l2norm(vector) == 0) {
            vector[0] = 1.0f;
        }
        return vector;
    }

    /**
     * 将单个 token 的特征写入向量：主桶 + 子串 n-gram 桶。
     *
     * @param vector 待写入的目标向量
     * @param token  词或单字符
     */
    private void addToken(float[] vector, String token) {
        // 主桶：token 的 hashCode 对维度取模，权重 +1
        int bucket = Math.floorMod(token.hashCode(), DIMENSION);
        vector[bucket] += 1.0f;
        // 子串特征：取 token 前几个字符位置的组合哈希，权重 +0.5，增强局部语义
        for (int i = 1; i < token.length() && i < 4; i++) {
            int subBucket = Math.floorMod((token + i).hashCode(), DIMENSION);
            vector[subBucket] += 0.5f;
        }
    }

    /**
     * 计算向量各分量平方和（L2 范数的平方），用于判断是否为全零向量。
     */
    private double l2norm(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        return sum;
    }

    /**
     * 对向量做 L2 归一化：每个分量除以模长，使向量长度为 1。
     */
    private void normalize(float[] vector) {
        double sum = l2norm(vector);
        // 全零向量无法归一化，直接返回
        if (sum == 0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }
}
