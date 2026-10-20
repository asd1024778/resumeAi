package com.resume.resumeio.repository;

import com.resume.resumeio.entity.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户上传简历数据访问层。
 * <p>
 * 继承 JpaRepository 获得基础的 CRUD 能力（save、findById、delete 等）。
 * 当前未定义自定义查询方法，简历的保存与按 ID 查询均使用 JpaRepository 内置方法。
 */
public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {
}
