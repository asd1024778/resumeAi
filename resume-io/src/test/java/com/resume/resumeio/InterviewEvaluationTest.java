package com.resume.resumeio;

import com.resume.resumeio.service.InterviewService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterviewEvaluationTest {

    @Test
    void parseQualifiedShouldDetectTag() {
        assertTrue(InterviewService.parseQualified("[合格]\n\n## 评价\n不错"));
        assertFalse(InterviewService.parseQualified("[不合格]\n\n## 改进建议\n请补充项目细节"));
        assertFalse(InterviewService.parseQualified("[不合格]\n\n但后面提到 [合格] 不应算"));
        assertFalse(InterviewService.parseQualified(""));
    }
}
