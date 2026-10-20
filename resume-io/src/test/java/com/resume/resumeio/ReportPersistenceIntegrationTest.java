package com.resume.resumeio;

import com.resume.resumeio.dto.AnalysisReportDto;
import com.resume.resumeio.dto.ReportRadarScoreDto;
import com.resume.resumeio.dto.ReportSummaryDto;
import com.resume.resumeio.repository.AnalysisReportRepository;
import com.resume.resumeio.repository.ResumeRepository;
import com.resume.resumeio.service.ReportStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ReportPersistenceIntegrationTest {

    @Autowired
    private ReportStoreService reportStoreService;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private AnalysisReportRepository analysisReportRepository;

    @Test
    @Transactional
    void saveAndLoadReportShouldPersistToDatabase() {
        AnalysisReportDto report = new AnalysisReportDto();
        report.setResumeTitle("测试简历");
        report.setScore(88);
        report.setSuggestions(java.util.List.of("建议一"));
        ReportRadarScoreDto radar = new ReportRadarScoreDto();
        radar.setCommunication(80);
        report.setRadar(radar);

        AnalysisReportDto saved = reportStoreService.saveWithResume(
                "test.pdf", "简历正文", report, 1L);

        assertTrue(saved.getReportId() > 0);
        assertEquals(saved.getReportId(), saved.getReportId());

        AnalysisReportDto loaded = reportStoreService.getByIdForUser(saved.getReportId(), 1L);
        assertEquals("测试简历", loaded.getResumeTitle());
        assertEquals(88, loaded.getScore());
        assertEquals(1, resumeRepository.count());
        assertEquals(1, analysisReportRepository.count());

        // 返回副本，修改不影响持久层
        loaded.setScore(0);
        AnalysisReportDto again = reportStoreService.getByIdForUser(saved.getReportId(), 1L);
        assertEquals(88, again.getScore());
    }

    @Test
    @Transactional
    void listSummariesForUserShouldReturnOwnedReportsNewestFirst() {
        AnalysisReportDto first = new AnalysisReportDto();
        first.setResumeTitle("旧报告");
        first.setScore(70);
        first.setRadar(new ReportRadarScoreDto());
        AnalysisReportDto savedFirst = reportStoreService.saveWithResume("a.pdf", "正文A", first, 2L);

        AnalysisReportDto second = new AnalysisReportDto();
        second.setResumeTitle("新报告");
        second.setJobTitle("Java");
        second.setScore(90);
        second.setRadar(new ReportRadarScoreDto());
        AnalysisReportDto savedSecond = reportStoreService.saveWithResume("b.pdf", "正文B", second, 2L);

        // 其他用户的报告不应出现
        AnalysisReportDto other = new AnalysisReportDto();
        other.setResumeTitle("他人");
        other.setScore(99);
        other.setRadar(new ReportRadarScoreDto());
        reportStoreService.saveWithResume("c.pdf", "正文C", other, 99L);

        List<ReportSummaryDto> list = reportStoreService.listSummariesForUser(2L);
        assertEquals(2, list.size());
        assertEquals(savedSecond.getReportId(), list.get(0).getReportId());
        assertEquals("新报告", list.get(0).getResumeTitle());
        assertEquals("Java", list.get(0).getJobTitle());
        assertEquals(90, list.get(0).getScore());
        assertNotNull(list.get(0).getCreatedAt());
        assertEquals(savedFirst.getReportId(), list.get(1).getReportId());
    }

    @Test
    @Transactional
    void deleteForUserShouldRemoveReportAndResume() {
        AnalysisReportDto report = new AnalysisReportDto();
        report.setResumeTitle("待删除");
        report.setScore(66);
        report.setRadar(new ReportRadarScoreDto());
        AnalysisReportDto saved = reportStoreService.saveWithResume("del.pdf", "正文", report, 3L);
        long id = saved.getReportId();

        reportStoreService.deleteForUser(id, 3L);

        assertFalse(analysisReportRepository.findByIdAndUserId(id, 3L).isPresent());
        assertFalse(resumeRepository.findById(id).isPresent());
        assertThrows(IllegalArgumentException.class,
                () -> reportStoreService.getByIdForUser(id, 3L));
    }
}
