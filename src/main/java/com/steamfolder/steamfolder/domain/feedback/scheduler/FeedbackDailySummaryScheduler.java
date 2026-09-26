package com.steamfolder.steamfolder.domain.feedback.scheduler;

import com.steamfolder.steamfolder.domain.feedback.service.analysis.FeedbackAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "feedback.analysis", name = "enabled", havingValue = "true")
public class FeedbackDailySummaryScheduler {

    private final FeedbackAnalysisService feedbackAnalysisService;

    @Scheduled(
            cron = "${feedback.analysis.daily-summary-cron:0 0 9 * * *}",
            zone = "${feedback.analysis.daily-summary-zone:Asia/Seoul}"
    )
    public void sendDailySummary() {
        feedbackAnalysisService.sendDailySummaryAsync();
    }
}
