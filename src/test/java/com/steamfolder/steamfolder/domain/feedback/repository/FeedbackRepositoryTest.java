package com.steamfolder.steamfolder.domain.feedback.repository;

import com.steamfolder.steamfolder.common.config.JpaAuditingConfig;
import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisStatus;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

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
        assertThat(savedFeedback.getCreatedAt()).isNotNull();
        assertThat(savedFeedback.getUpdatedAt()).isNotNull();
    }
}
