package com.resume.resumeio;

import com.resume.resumeio.service.RagKnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RagKnowledgeServiceTest {

    @Autowired
    private RagKnowledgeService ragKnowledgeService;

    @Test
    void indexAndSearchResumeShouldReturnRelevantChunk() {
        long userId = 99L;
        long resumeId = 1001L;
        String resume = """
                张三，Java 后端开发。精通 Spring Boot、MySQL、Redis。
                项目经历：电商订单系统，负责订单服务重构，QPS 提升 3 倍。
                教育：某某大学计算机本科。
                """;

        ragKnowledgeService.indexResume(userId, resumeId, resume);
        var docs = ragKnowledgeService.searchResume(userId, resumeId, "Spring Boot 订单系统", 3);

        assertFalse(docs.isEmpty());
        assertTrue(docs.get(0).getText().contains("订单") || docs.get(0).getText().contains("Spring"));
    }

    @Test
    void searchKnowledgeShouldReturnSeededContent() {
        // 内置知识库归属全局用户（userId=0），任意用户都应能检索到
        var docs = ragKnowledgeService.searchKnowledge("简历 STAR 法则 项目描述", 99L, 2);
        assertFalse(docs.isEmpty());
    }

    @Test
    void formatContextShouldWrapReferences() {
        var docs = ragKnowledgeService.searchKnowledge("面试技巧", 99L, 1);
        String ctx = ragKnowledgeService.formatContext(docs);
        assertTrue(ctx.contains("【通用参考知识】")
                || ctx.contains("【候选人本人简历】")
                || ctx.contains("未检索到"));
    }
}
