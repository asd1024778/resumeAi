package com.resume.resumeio.service;

import com.resume.resumeio.dto.RegisterRequest;
import com.resume.resumeio.dto.UpdateUserProfileRequest;
import com.resume.resumeio.dto.UserDto;
import com.resume.resumeio.dto.UserSensitiveProfileDto;
import com.resume.resumeio.entity.UserEntity;
import com.resume.resumeio.repository.UserRepository;
import com.resume.resumeio.util.PhoneUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户账号业务服务。
 * <p>
 * 业务职责：处理用户注册、登录认证、资料查询与敏感信息更新。
 * 账号基础信息（用户名、密码哈希、手机号）持久化在关系型数据库；
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
    /** 短信验证码服务，注册时校验手机号验证码 */
    private final SmsCodeService smsCodeService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserSensitiveProfileService sensitiveProfileService,
            SmsCodeService smsCodeService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sensitiveProfileService = sensitiveProfileService;
        this.smsCodeService = smsCodeService;
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
     * 手机号注册：格式校验 → 验证码校验 → 唯一性校验 → 落库 → 写敏感资料。
     * <p>
     * 业务顺序说明：先做「不消耗验证码」的轻量校验（手机号格式、密码强度、
     * 用户名/手机号唯一性），最后才校验并消费验证码，
     * 避免用户因为用户名被占用而白白浪费一条验证码。
     *
     * @param request 注册请求（手机号、验证码、密码、可选用户名与资料）
     * @return 脱敏后的用户信息
     * @throws IllegalArgumentException 任一项校验不通过，消息可直接展示给用户
     */
    @Transactional
    public UserDto register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("注册请求不能为空");
        }

        // 1. 手机号格式校验（核心逻辑，{@code 1[3-9]\d{9}}，自动兼容 +86 与分隔符）
        String phone = PhoneUtils.requireValid(request.getPhone());

        // 2. 密码强度：与登录页提示保持一致的最小长度
        String password = request.getPassword();
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于 6 位");
        }
        if (password.length() > 64) {
            throw new IllegalArgumentException("密码长度不能超过 64 位");
        }

        // 3. 用户名唯一性：留空时默认用手机号作为用户名
        String username = request.getUsername() == null || request.getUsername().isBlank()
                ? phone
                : request.getUsername().trim();
        if (username.length() > 64) {
            throw new IllegalArgumentException("用户名长度不能超过 64 个字符");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已被占用，请换一个");
        }

        // 4. 手机号唯一性：一个手机号只能注册一个账号
        if (userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("该手机号已注册，请直接登录");
        }

        // 5. 验证码校验（一次性消费）：不通过会抛异常，事务回滚
        smsCodeService.verifyRegisterCode(phone, request.getCode());

        // 6. 校验全部通过，写入数据库
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmail(blankToNull(request.getEmail()));
        user.setPhone(phone);
        UserEntity saved = userRepository.save(user);

        // 7. 敏感资料（含手机号）写入 Redis/内存，保证个人中心脱敏展示可用
        UserSensitiveProfileDto profile = UserSensitiveProfileDto.builder()
                .realName(request.getRealName())
                .phone(phone)
                .idCard(request.getIdCard())
                .address(request.getAddress())
                .socialAccount(request.getSocialAccount())
                .build();
        sensitiveProfileService.saveProfile(saved.getId(), profile);

        // 返回脱敏 DTO 给前端，避免泄露完整手机号等
        return UserDto.masked(saved, sensitiveProfileService.getProfile(saved.getId()));
    }

    /**
     * 空白字符串转 null，避免库里出现空串邮箱。
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
