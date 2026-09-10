package com.steamfolder.steamfolder.domain.feedback.entity;

import com.steamfolder.steamfolder.common.BaseTimeEntity;
import com.steamfolder.steamfolder.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feedbacks", indexes = {
        @Index(name = "idx_feedback_analysis_status_created_at", columnList = "analysis_status, created_at")
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
}
