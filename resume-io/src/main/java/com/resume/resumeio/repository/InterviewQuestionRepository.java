package com.resume.resumeio.repository;

import com.resume.resumeio.entity.InterviewQuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 面试题目数据访问层。
 * <p>
 * 继承 JpaRepository 获得基础的 CRUD 能力（save、findById、delete 等）。
 */
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestionEntity, Long> {

    /**
     * 按会话 ID 查询该场面试的全部题目，并按 questionOrder 升序排列。
     * <p>
     * 用途：加载面试题目列表、获取下一道题、生成复盘报告时遍历所有题目。
     *
     * @param sessionId 面试会话 ID
     * @return 该会话下的题目列表，按出题顺序排序
     */
    List<InterviewQuestionEntity> findBySessionIdOrderByQuestionOrderAsc(Long sessionId);

    /** 删除某会话下的全部题目 */
    void deleteBySessionId(Long sessionId);
}
