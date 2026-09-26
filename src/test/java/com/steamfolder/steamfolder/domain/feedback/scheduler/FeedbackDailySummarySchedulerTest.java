package com.steamfolder.steamfolder.domain.feedback.scheduler;

import com.steamfolder.steamfolder.domain.feedback.service.analysis.FeedbackAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FeedbackDailySummarySchedulerTest {

    @Test
    @DisplayName("설정된 시각의 스케줄 실행 시 비동기 일일 요약을 요청한다")
    void requestsDailySummary() {
        FeedbackAnalysisService analysisService = mock(FeedbackAnalysisService.class);
        FeedbackDailySummaryScheduler scheduler = new FeedbackDailySummaryScheduler(analysisService);

        scheduler.sendDailySummary();

        verify(analysisService).sendDailySummaryAsync();
    }
}
