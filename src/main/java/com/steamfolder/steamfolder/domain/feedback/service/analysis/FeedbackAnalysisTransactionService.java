package com.steamfolder.steamfolder.domain.feedback.service.analysis;

import com.steamfolder.steamfolder.domain.feedback.dto.analysis.FeedbackAnalysisResponse;
import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisFailureReason;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisStatus;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackPriority;
import com.steamfolder.steamfolder.domain.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FeedbackAnalysisTransactionService {

    private final FeedbackRepository feedbackRepository;

    @Transactional
    public Optional<FeedbackAnalysisTarget> startAnalysis(
            Long feedbackId,
            int maxAttempts,
            LocalDateTime staleBefore
    ) {
        Feedback feedback = feedbackRepository.findByIdForUpdate(feedbackId).orElse(null);
        if (feedback == null) {
            return Optional.empty();
        }

        if (feedback.getAnalysisStatus() == FeedbackAnalysisStatus.PROCESSING
                && feedback.getUpdatedAt() != null
                && feedback.getUpdatedAt().isBefore(staleBefore)
                && feedback.getAnalysisRetryCount() >= maxAttempts) {
            feedback.failStaleAnalysis(FeedbackAnalysisFailureReason.AI_TIMEOUT);
            return Optional.empty();
        }

        if (!feedback.canStartAnalysis(maxAttempts, staleBefore)) {
            return Optional.empty();
        }

        int attempt = feedback.startAnalysis();
        return Optional.of(new FeedbackAnalysisTarget(
                feedback.getId(),
                feedback.getCategory(),
                feedback.getContent(),
                feedback.getCreatedAt(),
                attempt
        ));
    }

    @Transactional
    public boolean completeAnalysis(
            Long feedbackId,
            int expectedAttempt,
            FeedbackAnalysisResponse response,
            LocalDateTime analyzedAt
    ) {
        Feedback feedback = feedbackRepository.findByIdForUpdate(feedbackId).orElse(null);
        if (feedback == null
                || feedback.getAnalysisStatus() != FeedbackAnalysisStatus.PROCESSING
                || feedback.getAnalysisRetryCount() != expectedAttempt) {
            return false;
        }

        feedback.completeAnalysis(expectedAttempt, response.priority(), response.summary(), analyzedAt);
        return true;
    }

    @Transactional
    public void failAnalysis(
            Long feedbackId,
            int expectedAttempt,
            FeedbackAnalysisFailureReason failureReason
    ) {
        feedbackRepository.findByIdForUpdate(feedbackId)
                .ifPresent(feedback -> feedback.failAnalysis(expectedAttempt, failureReason));
    }

    @Transactional(readOnly = true)
    public List<Long> findRecoveryCandidateIds(
            int maxAttempts,
            LocalDateTime staleBefore,
            int batchSize
    ) {
        return feedbackRepository.findAnalysisRecoveryCandidates(
                        FeedbackAnalysisStatus.PENDING,
                        FeedbackAnalysisStatus.FAILED,
                        FeedbackAnalysisStatus.PROCESSING,
                        maxAttempts,
                        staleBefore,
                        PageRequest.of(0, batchSize)
                ).stream()
                .map(Feedback::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingCriticalAlertIds(int batchSize) {
        return feedbackRepository
                .findByAnalysisStatusAndPriorityAndDiscordNotifiedAtIsNullOrderByCreatedAtAsc(
                        FeedbackAnalysisStatus.COMPLETED,
                        FeedbackPriority.P0,
                        PageRequest.of(0, batchSize)
                ).stream()
                .map(Feedback::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<FeedbackCriticalAlertTarget> findCriticalAlertTarget(Long feedbackId) {
        return feedbackRepository.findById(feedbackId)
                .filter(feedback -> feedback.getAnalysisStatus() == FeedbackAnalysisStatus.COMPLETED)
                .filter(feedback -> feedback.getPriority() == FeedbackPriority.P0)
                .filter(feedback -> feedback.getDiscordNotifiedAt() == null)
                .map(feedback -> new FeedbackCriticalAlertTarget(
                        feedback.getId(),
                        feedback.getCategory(),
                        feedback.getAiSummary()
                ));
    }

    @Transactional
    public void markDiscordNotified(Long feedbackId, LocalDateTime notifiedAt) {
        feedbackRepository.findByIdForUpdate(feedbackId)
                .ifPresent(feedback -> feedback.markDiscordNotified(notifiedAt));
    }

    @Transactional(readOnly = true)
    public List<FeedbackDailySummaryItem> findPendingDailySummaryItems(int batchSize) {
        return feedbackRepository.findPendingDailySummaryFeedbacks(
                        FeedbackAnalysisStatus.COMPLETED,
                        List.of(FeedbackPriority.P1, FeedbackPriority.P2),
                        PageRequest.of(0, batchSize)
                ).stream()
                .map(feedback -> new FeedbackDailySummaryItem(
                        feedback.getId(),
                        feedback.getPriority(),
                        feedback.getCategory(),
                        feedback.getAiSummary()
                ))
                .toList();
    }

    @Transactional
    public void markDiscordNotified(List<Long> feedbackIds, LocalDateTime notifiedAt) {
        feedbackIds.forEach(feedbackId -> feedbackRepository.findByIdForUpdate(feedbackId)
                .ifPresent(feedback -> feedback.markDiscordNotified(notifiedAt)));
    }
}
