package com.resume.resumeio.controller;

import com.resume.resumeio.dto.AnalysisReportDto;
import com.resume.resumeio.dto.ApiResponse;
import com.resume.resumeio.dto.ReportSummaryDto;
import com.resume.resumeio.security.SecurityUtils;
import com.resume.resumeio.service.ReportStoreService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 简历分析报告 API 控制器。
 * <p>
 * 职责：提供已保存的简历 AI 分析报告的查询接口。
 * 基础路径：{@code /api/report}
 * </p>
 */
@RestController
@RequestMapping("/api/report")
public class ReportController {

    /** 报告持久化服务，负责按用户隔离查询分析报告 */
    private final ReportStoreService reportStoreService;

    /**
     * 构造器注入 ReportStoreService。
     *
     * @param reportStoreService 报告存储服务
     */
    public ReportController(ReportStoreService reportStoreService) {
        this.reportStoreService = reportStoreService;
    }

    /**
     * 列出当前用户的全部分析报告摘要。
     * <p>
     * 路径：{@code GET /api/report/mine}<br>
     * 权限：需登录<br>
     * 出参：按创建时间倒序的摘要列表
     * </p>
     */
    @GetMapping("/mine")
    public Mono<ApiResponse<List<ReportSummaryDto>>> listMine() {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> Mono.fromCallable(() ->
                                ApiResponse.success(reportStoreService.listSummariesForUser(userId)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 根据报告 ID 获取简历分析报告。
     * <p>
     * 路径：{@code GET /api/report/{id}}<br>
     * 权限：需登录，且报告须属于当前用户<br>
     * 入参：{@code id}（路径变量，报告主键）<br>
     * 出参：{@link ApiResponse}{@code <}{@link AnalysisReportDto}{@code >}；
     *       403 无权访问、404 报告不存在
     * </p>
     */
    @GetMapping("/{id}")
    public Mono<ApiResponse<AnalysisReportDto>> getReport(@PathVariable long id) {
        // 从安全上下文获取当前登录用户 ID
        return SecurityUtils.currentUserId()
                .flatMap(userId -> Mono.fromCallable(() -> {
                            // 按 ID 查询报告，服务层内部校验 userId 归属
                            AnalysisReportDto report = reportStoreService.getByIdForUser(id, userId);
                            // 包装为统一成功响应
                            return ApiResponse.success(report);
                        })
                        // 数据库查询为阻塞操作，切换到弹性线程池
                        .subscribeOn(Schedulers.boundedElastic())
                        // 越权访问 → 403
                        .onErrorResume(SecurityException.class,
                                e -> Mono.just(new ApiResponse<>(403, e.getMessage(), null)))
                        // 报告不存在 → 404
                        .onErrorResume(IllegalArgumentException.class,
                                e -> Mono.just(new ApiResponse<>(404, e.getMessage(), null))));
    }

    /**
     * 删除当前用户的分析报告（含关联简历与面试记录）。
     * <p>
     * 路径：{@code DELETE /api/report/{id}}
     */
    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> deleteReport(@PathVariable long id) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> Mono.fromCallable(() -> {
                            reportStoreService.deleteForUser(id, userId);
                            return ApiResponse.<Void>success(null);
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(SecurityException.class,
                                e -> Mono.just(new ApiResponse<>(403, e.getMessage(), null)))
                        .onErrorResume(IllegalArgumentException.class,
                                e -> Mono.just(new ApiResponse<>(404, e.getMessage(), null))));
    }
}
