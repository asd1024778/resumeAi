package com.resume.resumeio.service;

import com.resume.resumeio.dto.AnalysisReportDto;
import com.resume.resumeio.dto.ReportRadarScoreDto;
import com.resume.resumeio.dto.ResumeScore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 分析报告数据格式转换器。
 * <p>
 * 业务职责：将 AI 大模型返回的 {@link ResumeScore} 结构
 * 映射为前端页面所需的 {@link AnalysisReportDto}（含雷达图维度、建议列表等）。
 * 不涉及持久化，reportId 由 {@link ReportStoreService} 在保存时分配。
 */
@Component
public class ReportMapper {

    /**
     * 将 AI 评分结果转换为前端报告 DTO。
     *
     * @param score    大模型结构化输出的评分对象
     * @param filename 原始上传文件名，用于展示报告标题
     * @return 前端可直接渲染的分析报告
     */
    public AnalysisReportDto toReport(ResumeScore score, String filename) {
        AnalysisReportDto report = new AnalysisReportDto();
        // 标题取文件名去掉扩展名
        report.setResumeTitle(stripExtension(filename));
        // 综合分直接映射
        report.setScore(score.getOverallScore());
        // 改进建议：null 安全转为空列表
        report.setSuggestions(score.getSuggestions() != null ? score.getSuggestions() : List.of());
        // 各维度分映射为雷达图五维结构
        report.setRadar(mapRadar(score.getDimensions()));
        return report;
    }

    /**
     * 将 AI 返回的维度 Map（键名可能不固定）映射为雷达图 DTO 的五个固定字段。
     * 支持多别名匹配与模糊包含匹配，提高对大模型输出格式的容错。
     *
     * @param dimensions 键为维度名、值为 0–100 分的 Map
     * @return 雷达图分数对象
     */
    private ReportRadarScoreDto mapRadar(Map<String, Integer> dimensions) {
        ReportRadarScoreDto radar = new ReportRadarScoreDto();
        if (dimensions == null || dimensions.isEmpty()) {
            return radar;
        }
        // 每个 setter 通过 getDimension 按别名查找对应分数
        radar.setCommunication(getDimension(dimensions, "沟通表达", "沟通能力", "表达"));
        radar.setProfessional(getDimension(dimensions, "技术能力", "专业技能", "专业能力"));
        radar.setLogic(getDimension(dimensions, "逻辑思维", "逻辑"));
        radar.setProject(getDimension(dimensions, "项目经验", "项目"));
        radar.setBasic(getDimension(dimensions, "基础知识", "基础"));
        return radar;
    }

    /**
     * 从 dimensions Map 中按多个候选键名查找分数。
     * 先精确匹配 keys，再遍历 entry 做 key.contains 模糊匹配，均未命中返回 0。
     *
     * @param dimensions AI 输出的维度分 Map
     * @param keys       候选维度名称（按优先级排列）
     * @return 匹配到的分数，默认 0
     */
    private int getDimension(Map<String, Integer> dimensions, String... keys) {
        // 第一轮：精确键名匹配
        for (String key : keys) {
            Integer value = dimensions.get(key);
            if (value != null) {
                return value;
            }
        }
        // 第二轮：维度 key 包含候选子串即视为匹配（容错 AI 命名差异）
        for (Map.Entry<String, Integer> entry : dimensions.entrySet()) {
            for (String key : keys) {
                if (entry.getKey().contains(key)) {
                    return entry.getValue();
                }
            }
        }
        return 0;
    }

    /**
     * 去掉文件扩展名作为展示标题；空文件名时使用默认文案。
     */
    private String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "简历分析报告";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }
}
