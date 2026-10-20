package com.resume.resumeio;

import com.resume.resumeio.dto.StartInterviewRequest;
import com.resume.resumeio.dto.StartInterviewResponse;
import com.resume.resumeio.dto.InterviewSessionDto;
import com.resume.resumeio.dto.SubmitAnswerRequest;
import com.resume.resumeio.dto.SubmitAnswerResponse;
import com.resume.resumeio.dto.AnalysisReportDto;
import com.resume.resumeio.dto.ReportRadarScoreDto;
import com.resume.resumeio.repository.InterviewSessionRepository;
import com.resume.resumeio.service.InterviewService;
import com.resume.resumeio.service.ReportStoreService;
import com.resume.resumeio.service.RagKnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InterviewConcurrencyIntegrationTest {

    @Autowired
    private InterviewService interviewService;

    @Autowired
    private ReportStoreService reportStoreService;

    @Autowired
    private RagKnowledgeService ragKnowledgeService;

    @Autowired
    private InterviewSessionRepository sessionRepository;

    private long userId = 1L;
    private long reportId;

    @BeforeEach
    void seedReport() {
        AnalysisReportDto report = new AnalysisReportDto();
        report.setResumeTitle("面试测试");
        report.setScore(90);
        report.setRadar(new ReportRadarScoreDto());
        long resumeId = reportStoreService.createResume("r.pdf", """
                候选人擅长 Java 与 Spring Boot，曾主导支付系统性能优化，将接口延迟降低 40%。
                """, userId);
        ragKnowledgeService.indexResume(userId, resumeId, """
                候选人擅长 Java 与 Spring Boot，曾主导支付系统性能优化，将接口延迟降低 40%。
                """);
        reportId = reportStoreService.saveReport(resumeId, report, userId).getReportId();
    }

    @Test
    @Transactional
    void submitAnswerShouldAdvanceSessionIndex() {
        StartInterviewRequest startReq = new StartInterviewRequest();
        startReq.setResumeId(reportId);
        StartInterviewResponse start = interviewService.start(startReq, userId);
        long sessionId = start.getSession().getId();
        long questionId = start.getFirstQuestion().getId();

        SubmitAnswerRequest answerReq = new SubmitAnswerRequest();
        answerReq.setSessionId(sessionId);
        answerReq.setQuestionId(questionId);
        answerReq.setAnswer("我的回答");
        answerReq.setQualified(false);
        SubmitAnswerResponse notPassed = interviewService.submit(answerReq, userId);
        assertNull(notPassed.getNextQuestion());
        assertEquals(0, sessionRepository.findById(sessionId).orElseThrow().getCurrentIndex());

        answerReq.setQualified(true);
        SubmitAnswerResponse response = interviewService.submit(answerReq, userId);

        assertNotNull(response.getNextQuestion());
        assertFalse(Boolean.TRUE.equals(response.getInterviewCompleted()));
        var session = sessionRepository.findById(sessionId).orElseThrow();
        assertEquals(1, session.getCurrentIndex());
        assertEquals("in_progress", session.getStatus());

        InterviewSessionDto ended = interviewService.endInterview(sessionId, userId);
        assertEquals("completed", ended.getStatus());
    }
}
