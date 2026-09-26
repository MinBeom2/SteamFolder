package com.steamfolder.steamfolder.domain.feedback.repository;

import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisStatus;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackPriority;
import com.steamfolder.steamfolder.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    void deleteByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select feedback from Feedback feedback where feedback.id = :feedbackId")
    Optional<Feedback> findByIdForUpdate(@Param("feedbackId") Long feedbackId);

    @Query("""
            select feedback from Feedback feedback
            where feedback.analysisStatus = :pendingStatus
               or (feedback.analysisStatus = :failedStatus
                   and feedback.analysisRetryCount < :maxAttempts)
               or (feedback.analysisStatus = :processingStatus
                   and feedback.updatedAt < :staleBefore)
            order by feedback.createdAt asc
            """)
    List<Feedback> findAnalysisRecoveryCandidates(
            @Param("pendingStatus") FeedbackAnalysisStatus pendingStatus,
            @Param("failedStatus") FeedbackAnalysisStatus failedStatus,
            @Param("processingStatus") FeedbackAnalysisStatus processingStatus,
            @Param("maxAttempts") int maxAttempts,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable
    );

    List<Feedback> findByAnalysisStatusAndPriorityAndDiscordNotifiedAtIsNullOrderByCreatedAtAsc(
            FeedbackAnalysisStatus analysisStatus,
            FeedbackPriority priority,
            Pageable pageable
    );

    @Query("""
            select feedback from Feedback feedback
            where feedback.analysisStatus = :completedStatus
              and feedback.priority in :priorities
              and feedback.discordNotifiedAt is null
            order by feedback.priority asc, feedback.analyzedAt asc
            """)
    List<Feedback> findPendingDailySummaryFeedbacks(
            @Param("completedStatus") FeedbackAnalysisStatus completedStatus,
            @Param("priorities") List<FeedbackPriority> priorities,
            Pageable pageable
    );
}
