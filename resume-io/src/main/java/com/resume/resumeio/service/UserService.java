package com.resume.resumeio.service;

import com.resume.resumeio.dto.RegisterRequest;
import com.resume.resumeio.dto.UpdateUserProfileRequest;
import com.resume.resumeio.dto.UserDto;
import com.resume.resumeio.dto.UserSensitiveProfileDto;
import com.resume.resumeio.entity.UserEntity;
import com.resume.resumeio.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户账号业务服务。
 * <p>
 * 业务职责：处理用户注册、登录认证、资料查询与敏感信息更新。
 * 账号基础信息（用户名、密码哈希）持久化在关系型数据库；
 * 敏感资料通过 {@link UserSensitiveProfileService} 存 Redis/内存，与账号表分离。
 */
@Service
public class UserService {

    /** 用户 JPA 仓储，操作 user 表 */
    private final UserRepository userRepository;
    /** BCrypt 等密码编码器，用于注册加密与登录校验 */
    private final PasswordEncoder passwordEncoder;
    /** 敏感资料读写服务（Redis Key：user:sensitive:{userId}） */
    private final UserSensitiveProfileService sensitiveProfileService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserSensitiveProfileService sensitiveProfileService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sensitiveProfileService = sensitiveProfileService;
    }

    /**
     * 登录认证：校验用户名存在且密码匹配。
     *
     * @param username 登录名
     * @param password 明文密码
     * @return 认证通过的用户实体
     * @throws BadCredentialsException 用户名不存在或密码错误（统一提示，防枚举）
     */
    @Transactional(readOnly = true)
    public UserEntity authenticate(String username, String password) {
        // 按用户名查库，不存在则抛 BadCredentialsException
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
        // matches：明文与库中 passwordHash 比对
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        return user;
    }

    /**
     * 用户注册：校验输入 → 创建账号 → 保存敏感资料 → 返回脱敏用户 DTO。
     *
     * @param request 注册请求（用户名、密码、邮箱及可选敏感字段）
     * @return 脱敏后的用户信息
     */
    @Transactional
    public UserDto register(RegisterRequest request) {
        // 基础字段校验
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于 6 位");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        // 构建用户实体并 BCrypt 加密密码
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        UserEntity saved = userRepository.save(user);

        // 将注册时提交的敏感字段写入 Redis/内存（与 DB 事务独立）
        UserSensitiveProfileDto profile = UserSensitiveProfileDto.builder()
                .realName(request.getRealName())
                .phone(request.getPhone())
                .idCard(request.getIdCard())
                .address(request.getAddress())
                .socialAccount(request.getSocialAccount())
                .build();
        sensitiveProfileService.saveProfile(saved.getId(), profile);

        // 返回脱敏 DTO 给前端，避免泄露完整手机号等
        return UserDto.masked(saved, sensitiveProfileService.getProfile(saved.getId()));
    }

    /**
     * 按 ID 查询用户，敏感字段以脱敏形式返回（常规 API 用）。
     */
    @Transactional(readOnly = true)
    public UserDto findMaskedById(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return UserDto.masked(user, sensitiveProfileService.getMaskedProfile(id));
    }

    /**
     * 按 ID 查询用户，敏感字段完整返回（需权限控制的内部/管理场景）。
     */
    @Transactional(readOnly = true)
    public UserDto findFullById(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return UserDto.full(user, sensitiveProfileService.getProfile(id));
    }

    /**
     * 按 ID 获取用户实体，供其他 Service 或 Security 上下文使用。
     */
    @Transactional(readOnly = true)
    public UserEntity findEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    /**
     * 更新用户敏感资料并返回脱敏后的用户 DTO。
     */
    public UserDto updateSensitiveProfile(Long userId, UpdateUserProfileRequest request) {
        // 先确认用户存在
        findEntityById(userId);
        UserSensitiveProfileDto updated = sensitiveProfileService.updateProfile(userId, request);
        UserEntity user = findEntityById(userId);
        return UserDto.masked(user, updated);
    }
}
