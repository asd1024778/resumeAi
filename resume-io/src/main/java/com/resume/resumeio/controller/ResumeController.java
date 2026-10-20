package com.resume.resumeio.controller;

import com.resume.resumeio.dto.AnalysisReportDto;
import com.resume.resumeio.dto.ApiResponse;
import com.resume.resumeio.dto.ResumeScore;
import com.resume.resumeio.security.SecurityUtils;
import com.resume.resumeio.service.DocumentParserService;
import com.resume.resumeio.service.ReportMapper;
import com.resume.resumeio.service.ReportStoreService;
import com.resume.resumeio.service.ResumeAnalysisService;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;

/**
 * 简历上传与分析 API 控制器。
 * <p>
 * 职责：接收用户上传的简历文件，解析文本、调用 AI 分析评分，并持久化分析报告。
 * 基础路径：{@code /api/resume}
 * </p>
 */
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    /** 文档解析服务，将简历文件转为纯文本 */
    private final DocumentParserService parserService;
    /** 简历 AI 分析服务，生成各维度评分 */
    private final ResumeAnalysisService analysisService;
    /** 报告 DTO 映射器，将评分结果转为前端展示结构 */
    private final ReportMapper reportMapper;
    /** 报告/简历持久化服务 */
    private final ReportStoreService reportStoreService;

    /**
     * 构造器注入依赖。
     *
     * @param parserService        文档解析服务
     * @param analysisService      简历分析服务
     * @param reportMapper         报告映射器
     * @param reportStoreService   报告存储服务
     */
    public ResumeController(
            DocumentParserService parserService,
            ResumeAnalysisService analysisService,
            ReportMapper reportMapper,
            ReportStoreService reportStoreService
    ) {
        this.parserService = parserService;
        this.analysisService = analysisService;
        this.reportMapper = reportMapper;
        this.reportStoreService = reportStoreService;
    }

    /**
     * 上传简历并触发 AI 分析。
     * <p>
     * 路径：{@code POST /api/resume/upload}<br>
     * 权限：需登录<br>
     * 入参：multipart/form-data — {@code file}（必填，简历文件）<br>
     * 出参：{@link ApiResponse}{@code <}{@link AnalysisReportDto}{@code >}，含评分与分析详情；失败时返回错误消息
     * </p>
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApiResponse<AnalysisReportDto>> uploadAndAnalyze(@RequestPart("file") FilePart file) {
        // 获取当前登录用户 ID，简历与报告按用户隔离存储
        return SecurityUtils.currentUserId()
                // 合并文件流的所有 DataBuffer 片段
                .flatMap(userId -> DataBufferUtils.join(file.content())
                        .flatMap(dataBuffer -> {
                            // 读取缓冲区内容为字节数组
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            // 释放 Netty 缓冲区
                            DataBufferUtils.release(dataBuffer);
                            // 记录原始文件名，用于报告展示
                            String filename = file.filename();

                            return Mono.fromCallable(() -> {
                                        // 解析简历二进制内容为纯文本
                                        String text = parserService.parse(new ByteArrayInputStream(bytes));
                                        // 创建简历记录并返回 resumeId
                                        long resumeId = reportStoreService.createResume(filename, text, userId);
                                        // 调用 AI 服务对简历文本进行多维度评分
                                        ResumeScore score = analysisService.analyze(userId, resumeId, text);
                                        // 将评分结果映射为前端报告 DTO
                                        AnalysisReportDto report = reportMapper.toReport(score, filename);
                                        // 持久化分析报告并返回带 ID 的完整 DTO
                                        AnalysisReportDto saved = reportStoreService.saveReport(
                                                resumeId, report, userId);
                                        return ApiResponse.success(saved);
                                    })
                                    // 解析、AI 调用、数据库写入均为阻塞操作
                                    .subscribeOn(Schedulers.boundedElastic())
                                    // 捕获解析或分析异常，返回友好错误信息
                                    .onErrorResume(e -> Mono.just(
                                            ApiResponse.error("简历解析或分析失败: " + e.getMessage())
                                    ));
                        }));
    }
}
