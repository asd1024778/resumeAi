package com.resume.resumeio.service;

import com.resume.resumeio.concurrency.DtoCopies;
import com.resume.resumeio.concurrency.SessionLockManager;
import com.resume.resumeio.dto.AnalysisReportDto;
import com.resume.resumeio.dto.ReportSummaryDto;
import com.resume.resumeio.entity.AnalysisReportEntity;
import com.resume.resumeio.entity.InterviewSessionEntity;
import com.resume.resumeio.entity.ResumeEntity;
import com.resume.resumeio.repository.AnalysisReportRepository;
import com.resume.resumeio.repository.InterviewQuestionRepository;
import com.resume.resumeio.repository.InterviewSessionRepository;
import com.resume.resumeio.repository.ResumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 简历与分析报告持久化服务。
 * <p>
 * 业务职责：管理简历原文（parsedText）与分析报告（JSON）在数据库中的 CRUD，
 * 约定 reportId 与 resumeId 一致，便于前端与 RAG 层统一使用同一 ID 引用一份简历。
 */
@Service
public class ReportStoreService {

    private final ResumeRepository resumeRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final SessionLockManager sessionLockManager;

    public ReportStoreService(
            ResumeRepository resumeRepository,
            AnalysisReportRepository analysisReportRepository,
            InterviewSessionRepository interviewSessionRepository,
            InterviewQuestionRepository interviewQuestionRepository,
            SessionLockManager sessionLockManager
    ) {
        this.resumeRepository = resumeRepository;
        this.analysisReportRepository = analysisReportRepository;
        this.interviewSessionRepository = interviewSessionRepository;
        this.interviewQuestionRepository = interviewQuestionRepository;
        this.sessionLockManager = sessionLockManager;
    }

    /**
     * 保存上传简历的解析文本，返回 resumeId 供 RAG 入库与 AI 分析链路使用。
     *
     * @param filename   原始文件名
     * @param parsedText Tika 提取的纯文本
     * @param userId     所属用户
     * @return 新简历记录的主键
     */
    @Transactional
    public long createResume(String filename, String parsedText, Long userId) {
        ResumeEntity resume = new ResumeEntity();
        resume.setUserId(userId);
        resume.setFilename(filename);
        resume.setParsedText(parsedText);
        return resumeRepository.save(resume).getId();
    }

    /**
     * 保存 AI 分析报告；reportId 与 resumeId 使用同一 ID。
     *
     * @param resumeId 已存在的简历 ID
     * @param report   分析报告 DTO（会被深拷贝后持久化）
     * @param userId   所属用户
     * @return 拷贝后的报告 DTO（避免调用方后续修改影响已存数据）
     */
    @Transactional
    public AnalysisReportDto saveReport(long resumeId, AnalysisReportDto report, Long userId) {
        report.setReportId(resumeId);

        AnalysisReportEntity entity = new AnalysisReportEntity();
        entity.setId(resumeId);
        entity.setUserId(userId);
        entity.setResumeId(resumeId);
        // DtoCopies 深拷贝，防止共享引用导致并发或后续修改污染库内数据
        entity.setReportData(DtoCopies.copyReport(report));
        analysisReportRepository.save(entity);

        return DtoCopies.copyReport(report);
    }

    /**
     * 一次性保存简历原文与分析报告（上传分析流程常用）。
     * reportId 与 resumeId 保持一致，前端只需记住一个 ID。
     *
     * @param filename   文件名
     * @param parsedText 解析文本
     * @param report     AI 分析结果
     * @param userId     用户 ID
     * @return 带 reportId 的报告 DTO
     */
    @Transactional
    public AnalysisReportDto saveWithResume(String filename, String parsedText, AnalysisReportDto report, Long userId) {
        long resumeId = createResume(filename, parsedText, userId);
        return saveReport(resumeId, report, userId);
    }

    /**
     * 按报告 ID 获取简历解析全文，供 RAG 重新入库或面试出题使用。
     *
     * @param reportId 报告/简历 ID（二者相同）
     * @param userId   当前用户，用于权限校验
     * @return 简历纯文本，找不到时返回空串
     */
    @Transactional(readOnly = true)
    public String getParsedTextByReportId(long reportId, long userId) {
        // 先校验报告归属当前用户
        AnalysisReportEntity report = analysisReportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new IllegalArgumentException("报告不存在"));
        // 再通过 resumeId 关联查原文
        return resumeRepository.findById(report.getResumeId())
                .map(ResumeEntity::getParsedText)
                .orElse("");
    }

    /**
     * 按 ID 获取用户的分析报告（只读，带权限校验）。
     */
    @Transactional(readOnly = true)
    public AnalysisReportDto getByIdForUser(long reportId, long userId) {
        AnalysisReportEntity entity = analysisReportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new IllegalArgumentException("报告不存在"));
        if (!entity.getUserId().equals(userId)) {
            throw new SecurityException("无权访问该报告");
        }
        return DtoCopies.copyReport(entity.getReportData());
    }

    /**
     * 判断指定报告是否属于当前用户（轻量级存在性检查）。
     */
    @Transactional(readOnly = true)
    public boolean existsForUser(long reportId, long userId) {
        return analysisReportRepository.findByIdAndUserId(reportId, userId).isPresent();
    }

    /**
     * 列出当前用户的全部分析报告摘要（最新在前）。
     */
    @Transactional(readOnly = true)
    public List<ReportSummaryDto> listSummariesForUser(long userId) {
        return analysisReportRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 删除当前用户的分析报告，并级联清理关联简历与面试会话/题目。
     */
    @Transactional
    public void deleteForUser(long reportId, long userId) {
        AnalysisReportEntity report = analysisReportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new IllegalArgumentException("报告不存在"));
        if (!report.getUserId().equals(userId)) {
            throw new SecurityException("无权删除该报告");
        }

        List<InterviewSessionEntity> sessions =
                interviewSessionRepository.findByReportIdAndUserId(reportId, userId);
        for (InterviewSessionEntity session : sessions) {
            Long sessionId = session.getId();
            if (sessionId == null) {
                continue;
            }
            interviewQuestionRepository.deleteBySessionId(sessionId);
            sessionLockManager.release(sessionId);
        }
        if (!sessions.isEmpty()) {
            interviewSessionRepository.deleteAll(sessions);
        }

        Long resumeId = report.getResumeId();
        analysisReportRepository.delete(report);
        if (resumeId != null) {
            resumeRepository.findById(resumeId).ifPresent(resume -> {
                if (Objects.equals(resume.getUserId(), userId)) {
                    resumeRepository.delete(resume);
                }
            });
        }
    }

    private ReportSummaryDto toSummary(AnalysisReportEntity entity) {
        ReportSummaryDto summary = new ReportSummaryDto();
        summary.setReportId(entity.getId());
        summary.setCreatedAt(entity.getCreatedAt());
        AnalysisReportDto data = entity.getReportData();
        if (data != null) {
            summary.setResumeTitle(data.getResumeTitle());
            summary.setJobTitle(data.getJobTitle());
            summary.setScore(data.getScore());
        }
        return summary;
    }
}
