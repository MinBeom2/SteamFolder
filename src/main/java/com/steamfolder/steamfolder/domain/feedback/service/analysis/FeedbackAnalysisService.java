package com.steamfolder.steamfolder.domain.feedback.service.analysis;

import com.steamfolder.steamfolder.common.monitoring.DiscordAlertService;
import com.steamfolder.steamfolder.domain.feedback.client.FeedbackAnalysisException;
import com.steamfolder.steamfolder.domain.feedback.client.OpenAiFeedbackAnalysisClient;
import com.steamfolder.steamfolder.domain.feedback.config.FeedbackAnalysisProperties;
import com.steamfolder.steamfolder.domain.feedback.dto.analysis.FeedbackAnalysisRequest;
import com.steamfolder.steamfolder.domain.feedback.dto.analysis.FeedbackAnalysisResponse;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisFailureReason;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackPriority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackAnalysisService {

    private final FeedbackAnalysisProperties properties;
    private final FeedbackAnalysisTransactionService transactionService;
    private final OpenAiFeedbackAnalysisClient analysisClient;
    private final DiscordAlertService discordAlertService;

    @Async("feedbackAnalysisTaskExecutor")
    public void analyzeAsync(Long feedbackId) {
        if (!properties.isEnabled()) {
            return;
        }

        LocalDateTime staleBefore = LocalDateTime.now().minus(properties.getStaleProcessingAfter());
        FeedbackAnalysisTarget target = transactionService
                .startAnalysis(feedbackId, properties.getMaxAttempts(), staleBefore)
                .orElse(null);
        if (target == null) {
            return;
        }

        try {
            FeedbackAnalysisResponse response = analysisClient.analyze(new FeedbackAnalysisRequest(
                    target.feedbackId(),
                    target.category(),
                    target.content(),
                    target.submittedAt()
            ));

            boolean completed = transactionService.completeAnalysis(
                    target.feedbackId(),
                    target.attempt(),
                    response,
                    LocalDateTime.now()
            );
            if (completed && response.priority() == FeedbackPriority.P0) {
                sendCriticalAlert(target.feedbackId());
            }
        } catch (FeedbackAnalysisException exception) {
            transactionService.failAnalysis(
                    target.feedbackId(),
                    target.attempt(),
                    exception.getFailureReason()
            );
            log.warn(
                    "Feedback analysis failed: feedbackId={}, attempt={}, reason={}",
                    target.feedbackId(),
                    target.attempt(),
                    exception.getFailureReason()
            );
        } catch (Exception exception) {
            transactionService.failAnalysis(
                    target.feedbackId(),
                    target.attempt(),
                    FeedbackAnalysisFailureReason.UNKNOWN_ERROR
            );
            log.error(
                    "Unexpected feedback analysis failure: feedbackId={}, attempt={}",
                    target.feedbackId(),
                    target.attempt(),
                    exception
            );
        }
    }

    @Async("feedbackAnalysisTaskExecutor")
    public void sendPendingCriticalAlertAsync(Long feedbackId) {
        if (!properties.isEnabled()) {
            return;
        }
        sendCriticalAlert(feedbackId);
    }

    @Async("feedbackAnalysisTaskExecutor")
    public void sendDailySummaryAsync() {
        if (!properties.isEnabled()) {
            return;
        }

        int sentItems = 0;
        while (sentItems < properties.getDailySummaryMaxItems()) {
            int remaining = properties.getDailySummaryMaxItems() - sentItems;
            int batchSize = Math.max(1, Math.min(properties.getDailySummaryBatchSize(), remaining));
            List<FeedbackDailySummaryItem> items = transactionService.findPendingDailySummaryItems(batchSize);
            if (items.isEmpty()) {
                return;
            }

            boolean sent = discordAlertService.notifyFeedbackDailySummary(
                    items.getFirst().feedbackId() + "-" + items.getLast().feedbackId(),
                    items.stream().map(FeedbackDailySummaryItem::toMessageLine).toList()
            );
            if (!sent) {
                return;
            }

            transactionService.markDiscordNotified(
                    items.stream().map(FeedbackDailySummaryItem::feedbackId).toList(),
                    LocalDateTime.now()
            );
            sentItems += items.size();
        }
    }

    private void sendCriticalAlert(Long feedbackId) {
        FeedbackCriticalAlertTarget target = transactionService
                .findCriticalAlertTarget(feedbackId)
                .orElse(null);
        if (target == null) {
            return;
        }

        boolean sent = discordAlertService.notifyCriticalFeedback(
                target.feedbackId(),
                target.category().name(),
                target.summary()
        );
        if (sent) {
            transactionService.markDiscordNotified(target.feedbackId(), LocalDateTime.now());
        }
    }
}
