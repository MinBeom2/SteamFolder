package com.steamfolder.steamfolder.domain.feedback.sync;

import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record FeedbackNotionSyncJob(
        Long feedbackId,
        long version,
        FeedbackNotionSyncAction action,
        String notionPageId,
        String category,
        String analysisStatus,
        String priority,
        String aiSummary,
        String analysisFailureReason,
        OffsetDateTime submittedAt,
        OffsetDateTime analyzedAt,
        OffsetDateTime discordNotifiedAt
) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static FeedbackNotionSyncJob from(FeedbackNotionSync sync, Feedback feedback) {
        if (sync.getAction() == FeedbackNotionSyncAction.ARCHIVE) {
            return new FeedbackNotionSyncJob(sync.getFeedbackId(), sync.getVersion(), sync.getAction(),
                    sync.getNotionPageId(), null, null, null, null, null, null, null, null);
        }
        return new FeedbackNotionSyncJob(
                sync.getFeedbackId(),
                sync.getVersion(),
                sync.getAction(),
                sync.getNotionPageId(),
                feedback.getCategory().name(),
                feedback.getAnalysisStatus().name(),
                feedback.getPriority() == null ? null : feedback.getPriority().name(),
                feedback.getAiSummary(),
                feedback.getAnalysisFailureReason() == null ? null : feedback.getAnalysisFailureReason().name(),
                atSeoul(feedback.getCreatedAt()),
                atSeoul(feedback.getAnalyzedAt()),
                atSeoul(feedback.getDiscordNotifiedAt())
        );
    }

    private static OffsetDateTime atSeoul(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(SEOUL).toOffsetDateTime();
    }
}
