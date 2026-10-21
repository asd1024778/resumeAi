/*
 * 包声明：Spring 配置类所在包。
 */
package com.resume.resumeio.config;

/*
 * 导入说明：
 * - ChatClient：Spring AI 对话客户端，封装 LLM 调用
 * - Configuration / Bean：声明配置类与 Bean 工厂方法
 * - Value：读取 application.properties / 环境变量中的配置项
 * - ApplicationRunner：应用启动完成后执行一次，用于打印 AI 配置自检信息
 * - SecretMask：密钥脱敏，避免把 API Key 明文写进日志
 */
import com.resume.resumeio.util.SecretMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 相关 Spring 配置。
 * <p>
 * 本类是项目中「大模型调用」的唯一装配点，职责：
 * <ol>
 *   <li>把 Spring AI 自动配置的 {@link ChatClient.Builder} 构建成全局唯一的 {@link ChatClient} Bean，
 *       业务 Service 一律注入这个 Bean，不再各自 {@code builder.build()}，
 *       保证模型、温度、超时等参数只在一处生效；</li>
 *   <li>启动时打印 AI 配置自检信息，其中 <b>API Key 只输出脱敏值</b>
 *       （{@code sk-1234****cdef}），未配置时给出明确警告而不是等到调用时才报错。</li>
 * </ol>
 * 密钥来源优先级（见 {@code application.properties}）：
 * {@code DEEPSEEK_API_KEY} &gt; {@code DASHSCOPE_API_KEY}（历史变量名，兼容保留）。
 */
@Configuration // 标记为配置类，Spring 启动时加载
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * 创建全局唯一的 AI 对话客户端 Bean。
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

    /**
     * 启动自检：打印实际生效的 AI 配置（密钥脱敏），配置缺失时提前告警。
     * <p>
     * 典型输出：
     * <pre>
     * AI 配置自检：base-url=https://api.deepseek.com/ model=deepseek-flash api-key=sk-1234****cdef
     * </pre>
     *
     * @param apiKey  实际生效的密钥（已按优先级解析）
     * @param baseUrl 模型服务地址
     * @param model   模型名
     * @return 启动后执行的检查器
     */
    @Bean
    public ApplicationRunner aiConfigReporter(
            @Value("${spring.ai.deepseek.api-key:}") String apiKey,
            @Value("${spring.ai.deepseek.base-url:}") String baseUrl,
            @Value("${spring.ai.deepseek.chat.options.model:}") String model
    ) {
        return args -> {
            // 注意：这里只能用脱敏值，任何时候都不要打印明文 Key
            log.info("AI 配置自检：base-url={} model={} api-key={}",
                    baseUrl, model, SecretMask.mask(apiKey));
            if (SecretMask.isBlank(apiKey)) {
                log.warn("未检测到 DeepSeek API Key：简历分析、模拟面试、AI 对话将不可用。"
                        + "请在环境变量或 .env 中设置 DEEPSEEK_API_KEY（见 .env.example）");
            }
        };
    }
}
