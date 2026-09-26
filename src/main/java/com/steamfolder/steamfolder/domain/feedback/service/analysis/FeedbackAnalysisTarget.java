package com.steamfolder.steamfolder.domain.feedback.service.analysis;

import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;

import java.time.LocalDateTime;

public record FeedbackAnalysisTarget(
        Long feedbackId,
        FeedbackCategory category,
        String content,
        LocalDateTime submittedAt,
        int attempt
) {
}
