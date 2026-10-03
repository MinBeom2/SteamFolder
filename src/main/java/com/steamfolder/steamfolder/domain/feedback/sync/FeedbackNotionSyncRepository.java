package com.steamfolder.steamfolder.domain.feedback.sync;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeedbackNotionSyncRepository extends JpaRepository<FeedbackNotionSync, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sync from FeedbackNotionSync sync where sync.feedbackId = :feedbackId")
    Optional<FeedbackNotionSync> findByIdForUpdate(@Param("feedbackId") Long feedbackId);

    @Query("""
            select sync from FeedbackNotionSync sync
            where sync.version > sync.acknowledgedVersion
              and sync.feedbackId > :afterId
            order by sync.feedbackId asc
            """)
    List<FeedbackNotionSync> findPendingAfter(@Param("afterId") long afterId, Pageable pageable);
}
