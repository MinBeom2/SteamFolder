package com.steamfolder.steamfolder.domain.feedback.dto.analysis;

import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;

import java.time.LocalDateTime;

public record FeedbackAnalysisRequest(
        Long feedbackId,
        FeedbackCategory category,
        String content,
        LocalDateTime submittedAt
) {
}
