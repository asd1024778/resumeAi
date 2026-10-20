package com.resume.resumeio.repository;

import com.resume.resumeio.entity.InterviewSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 模拟面试会话数据访问层。
 * <p>
 * 继承 JpaRepository 获得基础的 CRUD 能力。
 */
public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, Long> {

    /**
     * 按会话 ID 与用户 ID 联合查询，确保只能访问自己的面试会话。
     * <p>
     * 用途：提交答案、获取会话详情、流式点评等接口的权限校验，
     * 防止用户越权访问他人的面试记录。
     *
     * @param id     面试会话 ID
     * @param userId 当前登录用户 ID
     * @return 匹配的会话实体，不存在或不属于该用户则返回 Optional.empty()
     */
    Optional<InterviewSessionEntity> findByIdAndUserId(Long id, Long userId);

    /** 查找关联某报告的全部面试会话（删除报告时级联清理） */
    List<InterviewSessionEntity> findByReportIdAndUserId(Long reportId, Long userId);
}
