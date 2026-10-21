package com.resume.resumeio.repository;

import com.resume.resumeio.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户账户数据访问层。
 * <p>
 * 继承 JpaRepository 获得基础的 CRUD 能力。
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * 按用户名精确查询用户。
     * <p>
     * 用途：登录时根据用户名查找账户并校验密码。
     *
     * @param username 登录用户名
     * @return 匹配的用户实体，不存在则返回 Optional.empty()
     */
    Optional<UserEntity> findByUsername(String username);

    /**
     * 检查指定用户名是否已被注册。
     * <p>
     * 用途：注册接口校验用户名唯一性，避免重复注册。
     *
     * @param username 待检查的用户名
     * @return true 表示用户名已存在，false 表示可用
     */
    boolean existsByUsername(String username);

    /**
     * 按手机号精确查询用户。
     * <p>
     * 用途：为后续「手机号 + 验证码登录」「找回密码」预留。
     *
     * @param phone 11 位手机号
     * @return 匹配的用户实体，不存在则返回 Optional.empty()
     */
    Optional<UserEntity> findByPhone(String phone);

    /**
     * 检查指定手机号是否已被注册。
     * <p>
     * 用途：① 发送验证码前拦截重复注册；② 注册落库前做最终唯一性校验。
     *
     * @param phone 11 位手机号
     * @return true 表示该手机号已注册
     */
    boolean existsByPhone(String phone);
}
