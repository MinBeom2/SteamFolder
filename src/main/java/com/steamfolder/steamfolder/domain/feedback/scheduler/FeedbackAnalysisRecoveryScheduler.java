package com.steamfolder.steamfolder.domain.feedback.scheduler;

import com.steamfolder.steamfolder.domain.feedback.config.FeedbackAnalysisProperties;
import com.steamfolder.steamfolder.domain.feedback.service.analysis.FeedbackAnalysisService;
import com.steamfolder.steamfolder.domain.feedback.service.analysis.FeedbackAnalysisTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "feedback.analysis", name = "enabled", havingValue = "true")
public class FeedbackAnalysisRecoveryScheduler {

    private final FeedbackAnalysisProperties properties;
    private final FeedbackAnalysisTransactionService transactionService;
    private final FeedbackAnalysisService analysisService;

    @Scheduled(
            initialDelayString = "${feedback.analysis.recovery-initial-delay-ms:30000}",
            fixedDelayString = "${feedback.analysis.recovery-interval-ms:60000}"
    )
    public void recoverIncompleteAnalysis() {
        LocalDateTime staleBefore = LocalDateTime.now().minus(properties.getStaleProcessingAfter());
        transactionService.findRecoveryCandidateIds(
                        properties.getMaxAttempts(),
                        staleBefore,
                        properties.getRecoveryBatchSize()
                )
                .forEach(analysisService::analyzeAsync);
    }

    @Scheduled(
            initialDelayString = "${feedback.analysis.recovery-initial-delay-ms:30000}",
            fixedDelayString = "${feedback.analysis.recovery-interval-ms:60000}"
    )
    public void retryPendingCriticalAlerts() {
        transactionService.findPendingCriticalAlertIds(properties.getRecoveryBatchSize())
                .forEach(analysisService::sendPendingCriticalAlertAsync);
    }
}
