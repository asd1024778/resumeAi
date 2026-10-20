/*
 * 包声明：并发控制工具所在包。
 */
package com.resume.resumeio.concurrency;

/*
 * 导入说明：
 * - AnalysisReportDto / ReportRadarScoreDto：分析报告传输对象
 * - List：不可变列表拷贝
 */
import com.resume.resumeio.dto.AnalysisReportDto;

import java.util.List;

/**
 * DTO 防御性拷贝工具。
 * <p>
 * 从缓存或持久层取出的对象可能被外部修改；返回给 Controller 前深拷贝一份，
 * 避免并发读写或意外 mutate 污染内部状态。
 */
public final class DtoCopies {

    /** 工具类禁止实例化 */
    private DtoCopies() {
    }

    /**
     * 深拷贝 {@link AnalysisReportDto}，包括嵌套的 radar 与列表字段。
     *
     * @param source 源对象，可为 null
     * @return 独立副本；source 为 null 时返回 null
     */
    public static AnalysisReportDto copyReport(AnalysisReportDto source) {
        if (source == null) {
            return null;
        }
        AnalysisReportDto copy = new AnalysisReportDto();
        // 拷贝标量字段
        copy.setReportId(source.getReportId());
        copy.setResumeTitle(source.getResumeTitle());
        copy.setJobTitle(source.getJobTitle());
        copy.setScore(source.getScore());
        // 建议列表：创建不可变副本，防止外部 add/remove
        copy.setSuggestions(source.getSuggestions() != null ? List.copyOf(source.getSuggestions()) : List.of());
        // 雷达图分数：逐字段拷贝嵌套 DTO
        if (source.getRadar() != null) {
            var radar = source.getRadar();
            var radarCopy = new com.resume.resumeio.dto.ReportRadarScoreDto();
            radarCopy.setCommunication(radar.getCommunication());
            radarCopy.setProfessional(radar.getProfessional());
            radarCopy.setLogic(radar.getLogic());
            radarCopy.setProject(radar.getProject());
            radarCopy.setBasic(radar.getBasic());
            copy.setRadar(radarCopy);
        }
        // 评审列表：同样做不可变拷贝
        copy.setReviews(source.getReviews() != null ? List.copyOf(source.getReviews()) : List.of());
        return copy;
    }
}
