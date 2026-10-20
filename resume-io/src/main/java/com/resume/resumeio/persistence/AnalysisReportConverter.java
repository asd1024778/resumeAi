package com.resume.resumeio.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.resumeio.dto.AnalysisReportDto;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA 属性转换器：在 AnalysisReportDto 与数据库 TEXT/JSON 列之间互转。
 * <p>
 * 写入时将 DTO 序列化为 JSON 字符串；读取时将 JSON 反序列化为 DTO 对象。
 */
@Converter // 标记为 JPA AttributeConverter，可在实体字段上通过 @Convert 引用
public class AnalysisReportConverter implements AttributeConverter<AnalysisReportDto, String> {

    /** 全局共享的 Jackson 对象映射器，用于 JSON 序列化/反序列化 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 将 Java 对象（AnalysisReportDto）转换为数据库存储值（JSON 字符串）。
     * <p>
     * 在实体 persist/merge 时由 JPA 自动调用。
     *
     * @param attribute 实体中的 AnalysisReportDto 字段值
     * @return JSON 字符串；若 attribute 为 null 则返回 null
     */
    @Override
    public String convertToDatabaseColumn(AnalysisReportDto attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            // 将 DTO 对象序列化为 JSON 字符串写入数据库
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("分析报告序列化失败", e);
        }
    }

    /**
     * 将数据库列值（JSON 字符串）转换为 Java 对象（AnalysisReportDto）。
     * <p>
     * 在实体从数据库加载时由 JPA 自动调用。
     *
     * @param dbData 数据库 TEXT 列中的 JSON 字符串
     * @return 反序列化后的 AnalysisReportDto；若 dbData 为空则返回 null
     */
    @Override
    public AnalysisReportDto convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            // 将 JSON 字符串反序列化为 AnalysisReportDto 对象
            return MAPPER.readValue(dbData, AnalysisReportDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("分析报告反序列化失败", e);
        }
    }
}
