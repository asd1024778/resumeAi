package com.resume.resumeio.repository;

import com.resume.resumeio.entity.AnalysisReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 简历 AI 分析报告数据访问层。
 * <p>
 * 继承 JpaRepository 获得基础的 CRUD 能力。
 */
public interface AnalysisReportRepository extends JpaRepository<AnalysisReportEntity, Long> {

    /**
     * 按报告 ID 与用户 ID 联合查询，确保只能访问自己的分析报告。
     * <p>
     * 用途：获取报告详情、开始模拟面试前校验报告归属，
     * 防止用户越权读取他人的分析结果。
     *
     * @param id     分析报告 ID
     * @param userId 当前登录用户 ID
     * @return 匹配的报告实体，不存在或不属于该用户则返回 Optional.empty()
     */
    Optional<AnalysisReportEntity> findByIdAndUserId(Long id, Long userId);

    /**
     * 查询某用户的全部分析报告，按创建时间倒序（最新在前）。
     */
    List<AnalysisReportEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
