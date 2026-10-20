/*
 * 包声明：业务自定义异常所在包。
 */
package com.resume.resumeio.exception;

/**
 * 并发操作冲突异常。
 * <p>
 * 在以下场景抛出：
 * <ul>
 *   <li>JPA 乐观锁更新失败（版本号不匹配）</li>
 *   <li>{@link com.resume.resumeio.concurrency.SessionLockManager} 会话锁竞争失败</li>
 * </ul>
 * 由 {@link GlobalExceptionHandler} 捕获并返回 409 响应，提示前端刷新后重试。
 */
public class ConcurrentOperationException extends RuntimeException {

    /**
     * @param message 面向用户的错误描述
     */
    public ConcurrentOperationException(String message) {
        super(message);
    }
}
