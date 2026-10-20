package com.resume.resumeio.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 返回的简历评分结果 DTO（大模型原始输出格式）。
 * <p>
 * Spring AI 会将大模型输出的 JSON 自动映射为此 Java 对象，
 * 之后由 ReportMapper 转换为前端需要的 AnalysisReportDto。
 */
@Data
public class ResumeScore {

    /** 综合评分，范围 0~100 分 */
    private int overallScore;

    /** 各维度分数，键为中文维度名（如"技术能力"），值为对应分数 */
    private Map<String, Integer> dimensions;

    /** 简历的优势亮点列表 */
    private List<String> strengths;

    /** 简历的不足之处列表 */
    private List<String> weaknesses;

    /** AI 给出的具体改进建议列表 */
    private List<String> suggestions;
}
