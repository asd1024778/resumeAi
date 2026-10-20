package com.resume.resumeio.controller;

import com.resume.resumeio.concurrency.SessionLockManager;
import com.resume.resumeio.dto.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 开发环境调试 API 控制器（由原空壳 TestController 实体化而来）。
 * <p>
 * 职责：提供开发阶段的健康检查与并发策略说明，便于本地调试。
 * 基础路径：{@code /api/dev}<br>
 * 启用条件：仅在 {@code dev} profile 下注册，生产环境不可访问。
 * </p>
 */
@RestController
@Profile("dev")
@RequestMapping("/api/dev")
public class DevTestController {

    /** 面试会话锁管理器，用于 per-session 互斥控制 */
    private final SessionLockManager sessionLockManager;

    /**
     * 构造器注入 SessionLockManager。
     *
     * @param sessionLockManager 会话锁管理器
     */
    public DevTestController(SessionLockManager sessionLockManager) {
        this.sessionLockManager = sessionLockManager;
    }

    /**
     * 开发环境存活探测。
     * <p>
     * 路径：{@code GET /api/dev/ping}<br>
     * 权限：dev profile 下公开（无登录要求）<br>
     * 入参：无<br>
     * 出参：{@link ApiResponse}{@code <String>}，固定返回 {@code "dev-ok"}
     * </p>
     */
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        // 直接返回成功响应，确认 dev 控制器已加载
        return ApiResponse.success("dev-ok");
    }

    /**
     * 查询系统并发控制策略说明。
     * <p>
     * 路径：{@code GET /api/dev/concurrency-info}<br>
     * 权限：dev profile 下公开<br>
     * 入参：无<br>
     * 出参：{@link ApiResponse}{@code <Map<String, String>>}，各模块并发机制的文字描述
     * </p>
     */
    @GetMapping("/concurrency-info")
    public ApiResponse<Map<String, String>> concurrencyInfo() {
        // 返回各子系统采用的并发/持久化策略摘要，供开发者参考
        return ApiResponse.success(Map.of(
                "sessionLock", "ReentrantLock per interview sessionId",
                "interviewSession", "JPA @Version optimistic lock",
                "reportStore", "MySQL/JPA transactional persistence",
                "sensitiveProfile", "Redis or ConcurrentHashMap"
        ));
    }
}
