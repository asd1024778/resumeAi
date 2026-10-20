package com.resume.resumeio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应格式，前后端约定均使用此结构包装业务数据。
 * <p>
 * 前端 axios 拦截器读取 code 判断是否成功，再从 data 字段取实际业务数据。
 *
 * @param <T> data 字段的泛型类型，不同接口返回不同内容
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** HTTP 风格状态码：200 表示成功，500 表示业务失败 */
    private int code;

    /** 提示信息，失败时前端会展示给用户 */
    private String message;

    /** 实际的业务数据载荷，失败时通常为 null */
    private T data;

    /**
     * 快速构造成功响应。
     *
     * @param data 要返回的业务数据
     * @param <T>  数据类型
     * @return code=200、message="success" 的 ApiResponse
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    /**
     * 快速构造失败响应。
     *
     * @param message 错误提示信息
     * @param <T>     数据类型（data 为 null）
     * @return code=500 的 ApiResponse
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }
}
