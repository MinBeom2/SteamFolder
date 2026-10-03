package com.steamfolder.steamfolder.domain.feedback.sync;

import com.steamfolder.steamfolder.domain.feedback.config.FeedbackAnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "feedback.notion-sync", name = "enabled", havingValue = "true")
public class FeedbackNotionSyncBackfill {

    private final FeedbackNotionSyncService syncService;
    private final FeedbackAnalysisProperties analysisProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void seedExistingFeedback() {
        syncService.seedExisting(analysisProperties.getMaxAttempts());
    }
}
