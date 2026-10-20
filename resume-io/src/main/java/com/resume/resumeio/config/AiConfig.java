/*
 * 包声明：Spring 配置类所在包。
 */
package com.resume.resumeio.config;

/*
 * 导入说明：
 * - ChatClient：Spring AI 对话客户端，封装 LLM 调用
 * - Configuration / Bean：声明配置类与 Bean 工厂方法
 */
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 相关 Spring 配置。
 * <p>
 * 将 Spring AI 自动配置的 {@link ChatClient.Builder} 构建为可注入的 {@link ChatClient} Bean，
 * 供简历分析、面试、对话等 Service 使用。
 */
@Configuration // 标记为配置类，Spring 启动时加载
public class AiConfig {

    /**
     * 创建 AI 对话客户端 Bean。
     * <p>
     * Builder 由 Spring AI 根据 application.properties（API 地址、密钥、模型名等）自动注入。
     *
     * @param builder Spring AI 提供的 ChatClient 构建器
     * @return 可直接调用 LLM 的 ChatClient 实例
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build(); // 使用默认配置构建客户端
    }
}
