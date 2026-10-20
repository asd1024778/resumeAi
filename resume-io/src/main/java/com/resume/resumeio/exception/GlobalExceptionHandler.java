/*
 * 包声明：全局异常处理所在包。
 */
package com.resume.resumeio.exception;

/*
 * 导入说明：
 * - ApiResponse：统一 API 响应包装类
 * - ExceptionHandler / RestControllerAdvice：全局异常拦截注解
 */
import com.resume.resumeio.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 * <p>
 * 拦截 Controller 层抛出的异常，转换为统一的 {@link ApiResponse} JSON 响应，
 * 避免未捕获异常直接暴露堆栈给前端。
 */
@RestControllerAdvice // 作用于所有 @RestController
public class GlobalExceptionHandler {

    /**
     * 处理并发冲突异常：返回 HTTP 语义上的 409 状态码及友好提示。
     *
     * @param e 并发操作异常
     * @return code=409 的 ApiResponse
     */
    @ExceptionHandler(ConcurrentOperationException.class)
    public ApiResponse<String> handleConcurrentOperation(ConcurrentOperationException e) {
        return new ApiResponse<>(409, e.getMessage(), null);
    }

    /**
     * 兜底处理器：捕获所有未单独处理的 Exception。
     *
     * @param e 任意异常
     * @return 通用错误 ApiResponse
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<String> handleException(Exception e) {
        return ApiResponse.error("服务器错误: " + e.getMessage());
    }
}
