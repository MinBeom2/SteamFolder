package com.steamfolder.steamfolder.domain.feedback.scheduler;

import com.steamfolder.steamfolder.domain.feedback.config.FeedbackAnalysisProperties;
import com.steamfolder.steamfolder.domain.feedback.service.analysis.FeedbackAnalysisService;
import com.steamfolder.steamfolder.domain.feedback.service.analysis.FeedbackAnalysisTransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackAnalysisRecoverySchedulerTest {

    @Test
    @DisplayName("미완료 분석과 미전송 P0 알림을 다시 작업 큐에 넣는다")
    void recoversIncompleteWork() {
        FeedbackAnalysisProperties properties = new FeedbackAnalysisProperties();
        properties.setMaxAttempts(3);
        properties.setRecoveryBatchSize(20);
        properties.setStaleProcessingAfter(Duration.ofMinutes(5));
        FeedbackAnalysisTransactionService transactionService = mock(FeedbackAnalysisTransactionService.class);
        FeedbackAnalysisService analysisService = mock(FeedbackAnalysisService.class);
        FeedbackAnalysisRecoveryScheduler scheduler = new FeedbackAnalysisRecoveryScheduler(
                properties,
                transactionService,
                analysisService
        );
        when(transactionService.findRecoveryCandidateIds(eq(3), any(), eq(20)))
                .thenReturn(List.of(1L, 2L));
        when(transactionService.findPendingCriticalAlertIds(20)).thenReturn(List.of(3L));

        scheduler.recoverIncompleteAnalysis();
        scheduler.retryPendingCriticalAlerts();

        verify(analysisService).analyzeAsync(1L);
        verify(analysisService).analyzeAsync(2L);
        verify(analysisService).sendPendingCriticalAlertAsync(3L);
    }
}
