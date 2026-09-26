package com.steamfolder.steamfolder.domain.feedback.entity;

import com.steamfolder.steamfolder.common.BaseTimeEntity;
import com.steamfolder.steamfolder.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "feedbacks", indexes = {
        @Index(name = "idx_feedback_analysis_status_created_at", columnList = "analysis_status, created_at"),
        @Index(
                name = "idx_feedback_analysis_recovery",
                columnList = "analysis_status, analysis_retry_count, updated_at"
        ),
        @Index(
                name = "idx_feedback_critical_notification",
                columnList = "analysis_status, analysis_priority, discord_notified_at"
        )
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Feedback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private FeedbackCategory category;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", length = 20, nullable = false)
    private FeedbackAnalysisStatus analysisStatus = FeedbackAnalysisStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_priority", length = 10)
    private FeedbackPriority priority;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @Column(name = "discord_notified_at")
    private LocalDateTime discordNotifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_failure_reason", length = 50)
    private FeedbackAnalysisFailureReason analysisFailureReason;

    @Builder.Default
    @Column(name = "analysis_retry_count", nullable = false)
    private int analysisRetryCount = 0;

    public boolean canStartAnalysis(int maxAttempts, LocalDateTime staleBefore) {
        if (analysisRetryCount >= maxAttempts) {
            return false;
        }
        return analysisStatus == FeedbackAnalysisStatus.PENDING
                || analysisStatus == FeedbackAnalysisStatus.FAILED
                || (analysisStatus == FeedbackAnalysisStatus.PROCESSING
                && getUpdatedAt() != null
                && getUpdatedAt().isBefore(staleBefore));
    }

    public int startAnalysis() {
        analysisStatus = FeedbackAnalysisStatus.PROCESSING;
        analysisRetryCount++;
        analysisFailureReason = null;
        return analysisRetryCount;
    }

    public void completeAnalysis(
            int expectedAttempt,
            FeedbackPriority priority,
            String aiSummary,
            LocalDateTime analyzedAt
    ) {
        if (!isCurrentAttempt(expectedAttempt)) {
            return;
        }
        this.analysisStatus = FeedbackAnalysisStatus.COMPLETED;
        this.priority = priority;
        this.aiSummary = aiSummary;
        this.analyzedAt = analyzedAt;
        this.analysisFailureReason = null;
    }

    public void failAnalysis(int expectedAttempt, FeedbackAnalysisFailureReason failureReason) {
        if (!isCurrentAttempt(expectedAttempt)) {
            return;
        }
        this.analysisStatus = FeedbackAnalysisStatus.FAILED;
        this.analysisFailureReason = failureReason;
    }

    public void failStaleAnalysis(FeedbackAnalysisFailureReason failureReason) {
        if (analysisStatus == FeedbackAnalysisStatus.PROCESSING) {
            this.analysisStatus = FeedbackAnalysisStatus.FAILED;
            this.analysisFailureReason = failureReason;
        }
    }

    public void markDiscordNotified(LocalDateTime notifiedAt) {
        if (analysisStatus == FeedbackAnalysisStatus.COMPLETED) {
            this.discordNotifiedAt = notifiedAt;
        }
    }

    private boolean isCurrentAttempt(int expectedAttempt) {
        return analysisStatus == FeedbackAnalysisStatus.PROCESSING
                && analysisRetryCount == expectedAttempt;
    }
}
