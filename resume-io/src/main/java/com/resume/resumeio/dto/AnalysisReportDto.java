package com.resume.resumeio.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 发给前端的简历 AI 分析报告 DTO。
 * <p>
 * 字段名与前端 TypeScript 中的 AnalysisReport 一一对应，
 * 前端拿到 JSON 后可直接使用，无需额外转换。
 */
@Data
public class AnalysisReportDto {

    /** 报告唯一编号，与数据库 analysis_reports.id 一致 */
    private long reportId;

    /** 简历标题，通常取上传文件名（去掉扩展名） */
    private String resumeTitle;

    /** 目标岗位名称（可选，用户或 AI 指定） */
    private String jobTitle;

    /** 综合评分，范围 0~100 */
    private int score;

    /** AI 给出的改进建议列表，默认空列表 */
    private List<String> suggestions = new ArrayList<>();

    /** 雷达图五个维度的分数，默认新建空对象 */
    private ReportRadarScoreDto radar = new ReportRadarScoreDto();

    /** 逐题复盘列表（面试完成后才有数据，上传阶段为空列表） */
    private List<QuestionReviewDto> reviews = new ArrayList<>();
}
