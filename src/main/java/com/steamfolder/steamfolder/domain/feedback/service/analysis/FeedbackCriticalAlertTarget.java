package com.steamfolder.steamfolder.domain.feedback.service.analysis;

import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;

public record FeedbackCriticalAlertTarget(
        Long feedbackId,
        FeedbackCategory category,
        String summary
) {
}
