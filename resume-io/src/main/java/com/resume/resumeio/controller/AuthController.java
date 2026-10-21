package com.resume.resumeio.controller;

import com.resume.resumeio.dto.*;
import com.resume.resumeio.entity.UserEntity;
import com.resume.resumeio.security.JwtTokenProvider;
import com.resume.resumeio.security.SecurityUtils;
import com.resume.resumeio.service.SmsCodeService;
import com.resume.resumeio.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 认证与用户资料 API 控制器。
 * <p>
 * 职责：处理用户登录、手机号注册（含短信验证码）、当前用户信息查询及敏感资料更新。
 * 基础路径：{@code /api/auth}
 * </p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 用户业务服务，负责认证、注册、资料读写 */
    private final UserService userService;
    /** JWT 令牌生成器，登录成功后签发访问令牌 */
    private final JwtTokenProvider jwtTokenProvider;
    /** 短信验证码服务，手机号注册时下发与校验验证码 */
    private final SmsCodeService smsCodeService;

    /**
     * 构造器注入依赖。
     *
     * @param userService        用户服务
     * @param jwtTokenProvider   JWT 令牌提供者
     * @param smsCodeService     短信验证码服务
     */
    public AuthController(UserService userService,
                          JwtTokenProvider jwtTokenProvider,
                          SmsCodeService smsCodeService) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.smsCodeService = smsCodeService;
    }

    /**
     * 用户登录。
     * <p>
     * 路径：{@code POST /api/auth/login}<br>
     * 权限：公开接口，无需登录<br>
     * 入参：{@link LoginRequest}（username、password）<br>
     * 出参：{@link ApiResponse}{@code <}{@link LoginResponse}{@code >}，成功时含 JWT token 及脱敏用户信息；失败时 HTTP 401
     * </p>
     */
    @PostMapping("/login")
    public Mono<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        // 将阻塞式认证逻辑包装为 Mono，避免阻塞 WebFlux 事件循环线程
        return Mono.fromCallable(() -> {
                    // 校验用户名密码，失败时抛出 BadCredentialsException
                    UserEntity user = userService.authenticate(request.getUsername(), request.getPassword());
                    // 认证成功，根据用户 ID 和用户名生成 JWT 访问令牌
                    String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
                    // 组装登录响应：token + 脱敏后的用户 DTO
                    return ApiResponse.success(new LoginResponse(
                            token,
                            userService.findMaskedById(user.getId())
                    ));
                })
                // 在弹性线程池执行阻塞 I/O（数据库查询等）
                .subscribeOn(Schedulers.boundedElastic())
                // 捕获凭证错误，转换为 401 业务响应而非异常传播
                .onErrorResume(BadCredentialsException.class,
                        e -> Mono.just(new ApiResponse<>(401, e.getMessage(), null)));
    }

    /**
     * 发送注册短信验证码。
     * <p>
     * 路径：{@code POST /api/auth/sms-code}<br>
     * 权限：公开接口，无需登录<br>
     * 入参：{@link SendSmsCodeRequest}（phone）<br>
     * 出参：{@link ApiResponse}{@code <}{@link SendSmsCodeResponse}{@code >}，
     * 含有效期与冷却时间；开发期还会回传验证码本身（{@code app.sms.debug-return-code=true}）<br>
     * 失败：手机号格式错误、该号已注册、60 秒内重复发送 → code=500 + 可读提示
     * </p>
     */
    @PostMapping("/sms-code")
    public Mono<ApiResponse<SendSmsCodeResponse>> sendSmsCode(@RequestBody SendSmsCodeRequest request) {
        return Mono.fromCallable(() -> {
                    // 手机号格式校验 + 生成 6 位验证码 + 存 Redis/内存 + 下发（日志通道）
                    String phone = request == null ? null : request.getPhone();
                    return ApiResponse.success(smsCodeService.sendRegisterCode(phone));
                })
                .subscribeOn(Schedulers.boundedElastic())
                // 业务校验失败按统一格式返回，不让异常冒泡成 500 堆栈
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ApiResponse.error(e.getMessage())));
    }

    /**
     * 手机号注册（需短信验证码）。
     * <p>
     * 路径：{@code POST /api/auth/register}<br>
     * 权限：公开接口，无需登录<br>
     * 入参：{@link RegisterRequest}（phone、code、password，用户名可留空默认用手机号）<br>
     * 出参：{@link ApiResponse}{@code <}{@link LoginResponse}{@code >}，
     * 注册成功后直接签发 JWT，前端可免二次登录；失败时返回可读错误消息
     * </p>
     */
    @PostMapping("/register")
    public Mono<ApiResponse<LoginResponse>> register(@RequestBody RegisterRequest request) {
        return Mono.fromCallable(() -> {
                    // 校验验证码并落库，返回脱敏用户信息
                    UserDto created = userService.register(request);
                    // 注册即登录：复用登录接口的令牌签发逻辑
                    String token = jwtTokenProvider.generateToken(created.getId(), created.getUsername());
                    return ApiResponse.success(new LoginResponse(token, created));
                })
                .subscribeOn(Schedulers.boundedElastic())
                // 捕获参数校验失败（手机号格式、验证码错误、用户名/手机号重复），返回业务错误而非 500
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ApiResponse.error(e.getMessage())))
                // 并发注册兜底：两个请求同时通过 existsByPhone 检查时，由数据库唯一索引拦截，
                // 这里把约束冲突翻译成可读提示，避免前端看到 500 堆栈
                .onErrorResume(DataIntegrityViolationException.class,
                        e -> Mono.just(ApiResponse.error("该手机号或用户名已被注册，请直接登录")));
    }

    /**
     * 获取当前登录用户基本信息（敏感字段已脱敏）。
     * <p>
     * 路径：{@code GET /api/auth/me}<br>
     * 权限：需登录（从 SecurityContext 解析当前用户 ID）<br>
     * 入参：无<br>
     * 出参：{@link ApiResponse}{@code <}{@link UserDto}{@code >}，脱敏后的用户资料
     * </p>
     */
    @GetMapping("/me")
    public Mono<ApiResponse<UserDto>> me() {
        // 从安全上下文获取当前登录用户 ID
        return SecurityUtils.currentUserId()
                // 根据 userId 查询脱敏用户资料
                .flatMap(userId -> Mono.fromCallable(() -> ApiResponse.success(userService.findMaskedById(userId)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 获取当前用户完整敏感资料（不脱敏，仅本人可访问）。
     * <p>
     * 路径：{@code GET /api/auth/me/profile/full}<br>
     * 权限：需登录，且只能查看自己的完整资料<br>
     * 入参：无<br>
     * 出参：{@link ApiResponse}{@code <}{@link UserDto}{@code >}，含手机、邮箱等未脱敏字段
     * </p>
     */
    @GetMapping("/me/profile/full")
    public Mono<ApiResponse<UserDto>> meFullProfile() {
        // 从安全上下文获取当前用户 ID
        return SecurityUtils.currentUserId()
                // 查询完整用户资料（不脱敏）
                .flatMap(userId -> Mono.fromCallable(() -> ApiResponse.success(userService.findFullById(userId)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 更新当前用户敏感资料。
     * <p>
     * 路径：{@code PUT /api/auth/me/profile}<br>
     * 权限：需登录，仅可更新本人资料<br>
     * 入参：{@link UpdateUserProfileRequest}（待更新的敏感字段）<br>
     * 出参：{@link ApiResponse}{@code <}{@link UserDto}{@code >}，更新后的脱敏用户资料（写入 Redis/内存缓存）
     * </p>
     */
    @PutMapping("/me/profile")
    public Mono<ApiResponse<UserDto>> updateProfile(@RequestBody UpdateUserProfileRequest request) {
        // 从安全上下文获取当前用户 ID
        return SecurityUtils.currentUserId()
                // 更新敏感资料并返回脱敏后的最新 DTO
                .flatMap(userId -> Mono.fromCallable(() ->
                                ApiResponse.success(userService.updateSensitiveProfile(userId, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }
}
