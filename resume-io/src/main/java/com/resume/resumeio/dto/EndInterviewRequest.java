package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 结束长对话模拟面试的请求参数。
 */
@Data
public class EndInterviewRequest {

    /** 当前面试会话 ID */
    private long sessionId;
}
