/*
 * 包声明：Spring 配置类所在包。
 */
package com.resume.resumeio.config;

/*
 * 导入说明：
 * - RagConstants：RAG 全局常量（如全局用户 ID）
 * - RagKnowledgeService：知识文档索引与向量化服务
 * - CommandLineRunner：应用启动完成后执行的回调接口
 */
import com.resume.resumeio.rag.RagConstants;
import com.resume.resumeio.service.RagKnowledgeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 种子数据初始化器。
 * <p>
 * 应用启动后自动写入全局知识文档（简历规范、面试技巧、评分维度），
 * 供对话、简历分析、模拟面试等场景的向量检索使用。
 */
@Configuration
public class RagSeedInitializer {

    /**
     * 注册启动 Runner：依次索引三份内置知识文档。
     *
     * @param ragKnowledgeService 知识索引服务
     * @return 启动完成后执行的 CommandLineRunner
     */
    @Bean
    CommandLineRunner seedRagKnowledge(RagKnowledgeService ragKnowledgeService) {
        return args -> {
            // 索引「简历撰写规范」知识
            ragKnowledgeService.indexKnowledge(
                    "kb-resume-rules",       // 文档唯一 ID
                    "简历撰写规范",            // 文档标题
                    """
                    优秀简历应包含：清晰个人信息、求职意向、教育背景、项目经历、技能清单。
                    项目描述建议使用 STAR 法则：情境、任务、行动、结果，并尽量量化成果。
                    技术岗位应突出：编程语言、框架、数据库、中间件、部署运维经验。
                    避免错别字、排版混乱、经历空泛；一页纸为宜，校招可两页。
                    """,
                    Long.parseLong(RagConstants.GLOBAL_USER_ID) // 归属全局用户，所有用户可检索
            );

            // 索引「技术面试技巧」知识
            ragKnowledgeService.indexKnowledge(
                    "kb-interview-tips",
                    "技术面试技巧",
                    """
                    技术面试常见环节：自我介绍、项目深挖、基础知识、算法与场景设计、反问环节。
                    回答项目题时要讲清：个人职责、技术选型理由、难点与解决方案、可量化的业务结果。
                    基础知识应结合岗位准备：后端关注数据库索引、并发、网络；前端关注渲染、性能、工程化。
                    沟通表达要结构化：先结论后细节；不会的问题可说明思路与学习意愿。
                    """,
                    Long.parseLong(RagConstants.GLOBAL_USER_ID)
            );

            // 索引「简历评分维度说明」知识
            ragKnowledgeService.indexKnowledge(
                    "kb-scoring-rubric",
                    "简历评分维度说明",
                    """
                    HR 与技术面试官常从以下维度评分（0-100）：
                    技术能力：与岗位匹配的专业技能深度与广度。
                    项目经验：项目复杂度、个人贡献、成果可信度。
                    沟通表达：文字条理、亮点是否突出。
                    逻辑思维：经历描述是否因果清晰。
                    基础知识：计算机基础与岗位核心知识掌握程度。
                    """,
                    Long.parseLong(RagConstants.GLOBAL_USER_ID)
            );
        };
    }
}
