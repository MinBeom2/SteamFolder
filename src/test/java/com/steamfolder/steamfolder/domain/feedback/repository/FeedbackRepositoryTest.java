package com.steamfolder.steamfolder.domain.feedback.repository;

import com.steamfolder.steamfolder.common.config.JpaAuditingConfig;
import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisStatus;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackPriority;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class FeedbackRepositoryTest {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("피드백 저장 시 사용자 연관관계와 초기 분석 상태 및 감사 시간이 기록됨")
    void saveFeedback_WithPendingStatusAndAuditFields() {
        // given
        User user = userRepository.save(User.builder()
                .steamId("76561198000000001")
                .username("FeedbackUser")
                .build());

        Feedback feedback = feedbackRepository.save(Feedback.builder()
                .user(user)
                .category(FeedbackCategory.BUG_REPORT)
                .content("스팀 동기화 진행률이 멈춥니다.")
                .build());

        entityManager.flush();
        entityManager.clear();

        // when
        Feedback savedFeedback = feedbackRepository.findById(feedback.getId()).orElseThrow();

        // then
        assertThat(savedFeedback.getUser().getId()).isEqualTo(user.getId());
        assertThat(savedFeedback.getCategory()).isEqualTo(FeedbackCategory.BUG_REPORT);
        assertThat(savedFeedback.getContent()).isEqualTo("스팀 동기화 진행률이 멈춥니다.");
        assertThat(savedFeedback.getAnalysisStatus()).isEqualTo(FeedbackAnalysisStatus.PENDING);
        assertThat(savedFeedback.getPriority()).isNull();
        assertThat(savedFeedback.getAiSummary()).isNull();
        assertThat(savedFeedback.getAnalyzedAt()).isNull();
        assertThat(savedFeedback.getDiscordNotifiedAt()).isNull();
        assertThat(savedFeedback.getAnalysisFailureReason()).isNull();
        assertThat(savedFeedback.getAnalysisRetryCount()).isZero();
        assertThat(savedFeedback.getCreatedAt()).isNotNull();
        assertThat(savedFeedback.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("완료된 P0 중 Discord 미전송 피드백만 조회")
    void findPendingCriticalNotifications() {
        User user = userRepository.save(User.builder()
                .steamId("76561198000000002")
                .username("CriticalFeedbackUser")
                .build());
        Feedback feedback = feedbackRepository.save(Feedback.builder()
                .user(user)
                .category(FeedbackCategory.BUG_REPORT)
                .content("로그인이 계속 실패하는 긴급 장애입니다.")
                .build());
        int attempt = feedback.startAnalysis();
        feedback.completeAnalysis(attempt, FeedbackPriority.P0, "로그인 장애", LocalDateTime.now());
        feedbackRepository.saveAndFlush(feedback);

        var pendingAlerts = feedbackRepository
                .findByAnalysisStatusAndPriorityAndDiscordNotifiedAtIsNullOrderByCreatedAtAsc(
                        FeedbackAnalysisStatus.COMPLETED,
                        FeedbackPriority.P0,
                        PageRequest.of(0, 10)
                );

        assertThat(pendingAlerts).extracting(Feedback::getId).containsExactly(feedback.getId());
    }
}
