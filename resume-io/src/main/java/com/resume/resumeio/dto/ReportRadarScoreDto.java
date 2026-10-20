package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 雷达图五个维度的分数 DTO。
 * <p>
 * 字段名使用英文是为与前端 ECharts 雷达图配置保持一致。
 */
@Data
public class ReportRadarScoreDto {

    /** 沟通表达能力得分，0~100 */
    private int communication;

    /** 专业技能得分，0~100 */
    private int professional;

    /** 逻辑思维能力得分，0~100 */
    private int logic;

    /** 项目经验得分，0~100 */
    private int project;

    /** 基础知识得分，0~100 */
    private int basic;
}
