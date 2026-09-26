package com.steamfolder.steamfolder.domain.feedback.event;

import com.steamfolder.steamfolder.domain.feedback.service.analysis.FeedbackAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "feedback.analysis", name = "enabled", havingValue = "true")
public class FeedbackAnalysisEventListener {

    private final FeedbackAnalysisService feedbackAnalysisService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeedbackCreated(FeedbackCreatedEvent event) {
        feedbackAnalysisService.analyzeAsync(event.feedbackId());
    }
}
