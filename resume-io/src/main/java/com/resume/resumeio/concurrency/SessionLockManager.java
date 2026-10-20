/*
 * 包声明：并发控制工具所在包。
 */
package com.resume.resumeio.concurrency;

/*
 * 导入说明：
 * - Component：注册为 Spring 单例 Bean
 * - ConcurrentHashMap / ReentrantLock：线程安全的锁映射与可重入锁
 */
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 面试会话级进程内互斥锁管理器。
 * <p>
 * 同一 sessionId 的写操作（提交答案、更新进度等）需先获取锁，
 * 配合 JPA {@code @Version} 乐观锁，防止并发请求导致数据乱序或覆盖。
 */
@Component
public class SessionLockManager {

    /** sessionId → 可重入锁 的映射，按需懒创建 */
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * 获取指定会话对应的锁对象（不存在则创建）。
     *
     * @param sessionId 面试会话 ID
     * @return 该会话专用的 ReentrantLock
     */
    public ReentrantLock lockFor(long sessionId) {
        return locks.computeIfAbsent(sessionId, id -> new ReentrantLock());
    }

    /**
     * 会话结束或销毁后释放锁条目，防止 Map 无限增长造成内存泄漏。
     *
     * @param sessionId 面试会话 ID
     */
    public void release(long sessionId) {
        locks.remove(sessionId);
    }
}
