package com.resume.resumeio.controller;

import com.resume.resumeio.dto.*;
import com.resume.resumeio.exception.ConcurrentOperationException;
import com.resume.resumeio.security.SecurityUtils;
import com.resume.resumeio.service.InterviewService;
import com.resume.resumeio.service.ReportStoreService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 模拟面试 API 控制器。
 * <p>
 * 职责：管理面试会话生命周期——开始面试、提交答案、流式获取 AI 反馈。
 * 基础路径：{@code /api/interview}
 * </p>
 */
@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    /** 面试业务服务，负责出题、评分、会话状态管理 */
    private final InterviewService interviewService;
    /** 报告/简历存储服务，用于校验用户对简历的所有权 */
    private final ReportStoreService reportStoreService;

    /**
     * 构造器注入依赖。
     *
     * @param interviewService    面试服务
     * @param reportStoreService  报告存储服务
     */
    public InterviewController(InterviewService interviewService, ReportStoreService reportStoreService) {
        this.interviewService = interviewService;
        this.reportStoreService = reportStoreService;
    }

    /**
     * 开始一场模拟面试。
     * <p>
     * 路径：{@code POST /api/interview/start}<br>
     * 权限：需登录，且 resumeId 须属于当前用户<br>
     * 入参：{@link StartInterviewRequest}（resumeId 等）<br>
     * 出参：{@link ApiResponse}{@code <}{@link StartInterviewResponse}{@code >}，含 sessionId 与首题；无权时 403
     * </p>
     */
    @PostMapping("/start")
    public Mono<ApiResponse<StartInterviewResponse>> start(@RequestBody StartInterviewRequest request) {
        // 获取当前登录用户 ID
        return SecurityUtils.currentUserId()
                .flatMap(userId -> Mono.fromCallable(() -> {
                            // 校验该简历是否属于当前用户，防止越权开始面试
                            if (!reportStoreService.existsForUser(request.getResumeId(), userId)) {
                                throw new SecurityException("无权使用该简历开始面试");
                            }
                            // 创建面试会话并返回首题等信息
                            return ApiResponse.success(interviewService.start(request, userId));
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        // 越权访问转换为 403 业务响应
                        .onErrorResume(SecurityException.class,
                                e -> Mono.just(new ApiResponse<>(403, e.getMessage(), null))));
    }

    /**
     * 提交面试答案并获取下一题/评分反馈。
     * <p>
     * 路径：{@code POST /api/interview/answer}<br>
     * 权限：需登录，且 session 须属于当前用户<br>
     * 入参：{@link SubmitAnswerRequest}（sessionId、answer 等）<br>
     * 出参：{@link ApiResponse}{@code <}{@link SubmitAnswerResponse}{@code >}；
     *       403 无权、404 会话不存在、409 并发冲突
     * </p>
     */
    @PostMapping("/answer")
    public Mono<ApiResponse<SubmitAnswerResponse>> submit(@RequestBody SubmitAnswerRequest request) {
        // 获取当前登录用户 ID
        return SecurityUtils.currentUserId()
                .flatMap(userId -> Mono.fromCallable(() ->
                                // 提交答案，服务层内部校验 session 归属与乐观锁
                                ApiResponse.success(interviewService.submit(request, userId)))
                        .subscribeOn(Schedulers.boundedElastic())
                        // 无权操作该会话 → 403
                        .onErrorResume(SecurityException.class,
                                e -> Mono.just(new ApiResponse<>(403, e.getMessage(), null)))
                        // 会话或题目不存在 → 404
                        .onErrorResume(IllegalArgumentException.class,
                                e -> Mono.just(new ApiResponse<>(404, e.getMessage(), null)))
                        // 乐观锁或会话锁冲突 → 409
                        .onErrorResume(ConcurrentOperationException.class,
                                e -> Mono.just(new ApiResponse<>(409, e.getMessage(), null))));
    }

    /**
     * 主动结束长对话模拟面试。
     * <p>
     * 路径：{@code POST /api/interview/end}
     */
    @PostMapping("/end")
    public Mono<ApiResponse<InterviewSessionDto>> end(@RequestBody EndInterviewRequest request) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> Mono.fromCallable(() ->
                                ApiResponse.success(interviewService.endInterview(request.getSessionId(), userId)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(SecurityException.class,
                                e -> Mono.just(new ApiResponse<>(403, e.getMessage(), null)))
                        .onErrorResume(IllegalArgumentException.class,
                                e -> Mono.just(new ApiResponse<>(404, e.getMessage(), null))));
    }

    /**
     * 流式获取面试 AI 反馈（Server-Sent Events）。
     * <p>
     * 路径：{@code POST /api/interview/stream}<br>
     * 权限：需登录<br>
     * 入参：{@link InterviewStreamRequest}（sessionId、上下文等）<br>
     * 出参：{@code Flux<String>}，{@code text/event-stream} 格式，逐块推送 AI 点评
     * </p>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody InterviewStreamRequest request) {
        // 获取当前登录用户 ID
        return SecurityUtils.currentUserId()
                // 委托服务层生成流式反馈文本
                .flatMapMany(userId -> interviewService.streamFeedback(request, userId));
    }
}
