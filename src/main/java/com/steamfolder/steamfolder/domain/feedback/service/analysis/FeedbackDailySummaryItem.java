package com.steamfolder.steamfolder.domain.feedback.service.analysis;

import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackPriority;

public record FeedbackDailySummaryItem(
        Long feedbackId,
        FeedbackPriority priority,
        FeedbackCategory category,
        String summary
) {

    private static final int MAX_SUMMARY_LENGTH = 120;

    public String toMessageLine() {
        return "[" + priority + "] #" + feedbackId + " " + category + " - " + truncateSummary();
    }

    private String truncateSummary() {
        if (summary == null) {
            return "요약 없음";
        }
        String normalized = summary.trim();
        if (normalized.length() <= MAX_SUMMARY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SUMMARY_LENGTH - 3) + "...";
    }
}
