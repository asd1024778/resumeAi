/*
 * 包声明：Spring 配置类所在包。
 */
package com.resume.resumeio.config;

/*
 * 导入说明：
 * - AbstractFailureAnalyzer / FailureAnalysis：Spring Boot 启动失败分析扩展点
 */
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * 数据库连接失败时的友好错误分析器。
 * <p>
 * 当 MySQL 未启动或连接参数错误时，Spring 默认只输出冗长堆栈；
 * 本类在检测到 "Communications link failure" 时，返回简短的中文排查建议。
 * <p>
 * 需在 META-INF/spring.factories 或 spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * 中注册方可生效（若项目已配置）。
 */
public class DatabaseConnectionFailureAnalyzer
        extends AbstractFailureAnalyzer<org.springframework.beans.factory.BeanCreationException> {

    /**
     * 分析 Bean 创建失败根因，若为 MySQL 连接失败则生成友好提示。
     *
     * @param rootFailure 异常链根节点
     * @param cause       触发的 BeanCreationException
     * @return 失败分析结果；无法识别时返回 null，交由其它 Analyzer 处理
     */
    @Override
    protected FailureAnalysis analyze(Throwable rootFailure,
                                      org.springframework.beans.factory.BeanCreationException cause) {
        // 沿 cause 链向下查找 MySQL 通信失败异常
        Throwable deep = findDeepCause(rootFailure);
        if (deep == null) {
            return null; // 非数据库连接问题，不处理
        }
        String message = deep.getMessage();
        if (message == null || !message.contains("Communications link failure")) {
            return null;
        }
        // 构造中文描述 + 排查步骤 + 原始异常
        return new FailureAnalysis(
                "无法连接 MySQL 数据库（Communications link failure）。",
                "请确认：\n"
                        + "1. MySQL 服务已启动（Windows 服务名通常为 MySQL80）\n"
                        + "2. application.properties 中的用户名/密码正确（默认 root/123456）\n"
                        + "3. 若暂时不用 MySQL，可用 dev 配置启动：mvn spring-boot:run \"-Dspring-boot.run.profiles=dev\"",
                cause
        );
    }

    /**
     * 递归遍历异常 cause 链，找到包含 "Communications link failure" 的底层异常。
     *
     * @param t 起始异常
     * @return 匹配的底层异常，未找到则 null
     */
    private Throwable findDeepCause(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("Communications link failure")) {
                return current;
            }
            current = current.getCause(); // 继续向下追溯
        }
        return null;
    }
}
