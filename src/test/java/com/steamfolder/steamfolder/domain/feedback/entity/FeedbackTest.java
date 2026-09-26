package com.steamfolder.steamfolder.domain.feedback.entity;

import com.steamfolder.steamfolder.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackTest {

    @Test
    @DisplayName("분석 성공 시 우선순위, 요약, 분석 시각을 저장하고 실패 사유를 제거한다")
    void completeAnalysisStoresResult() {
        Feedback feedback = createFeedback();
        int attempt = feedback.startAnalysis();
        LocalDateTime analyzedAt = LocalDateTime.of(2026, 9, 21, 10, 0);

        feedback.completeAnalysis(attempt, FeedbackPriority.P0, "긴급 장애 피드백", analyzedAt);

        assertThat(feedback.getAnalysisStatus()).isEqualTo(FeedbackAnalysisStatus.COMPLETED);
        assertThat(feedback.getPriority()).isEqualTo(FeedbackPriority.P0);
        assertThat(feedback.getAiSummary()).isEqualTo("긴급 장애 피드백");
        assertThat(feedback.getAnalyzedAt()).isEqualTo(analyzedAt);
        assertThat(feedback.getAnalysisFailureReason()).isNull();
        assertThat(feedback.getAnalysisRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("분석 실패 시 원문은 유지하고 결과 필드는 비워 둔다")
    void failAnalysisPreservesOriginalFeedback() {
        Feedback feedback = createFeedback();
        int attempt = feedback.startAnalysis();

        feedback.failAnalysis(attempt, FeedbackAnalysisFailureReason.N8N_TIMEOUT);

        assertThat(feedback.getAnalysisStatus()).isEqualTo(FeedbackAnalysisStatus.FAILED);
        assertThat(feedback.getAnalysisFailureReason()).isEqualTo(FeedbackAnalysisFailureReason.N8N_TIMEOUT);
        assertThat(feedback.getContent()).isEqualTo("게임 동기화가 멈춥니다.");
        assertThat(feedback.getPriority()).isNull();
        assertThat(feedback.getAiSummary()).isNull();
        assertThat(feedback.getAnalyzedAt()).isNull();
        assertThat(feedback.getDiscordNotifiedAt()).isNull();
    }

    @Test
    @DisplayName("이전 시도의 늦은 응답은 현재 분석 결과를 덮어쓰지 않는다")
    void ignoresLateResponseFromPreviousAttempt() {
        Feedback feedback = createFeedback();
        int firstAttempt = feedback.startAnalysis();
        feedback.failAnalysis(firstAttempt, FeedbackAnalysisFailureReason.N8N_TIMEOUT);
        int secondAttempt = feedback.startAnalysis();

        feedback.completeAnalysis(firstAttempt, FeedbackPriority.P0, "늦은 응답", LocalDateTime.now());

        assertThat(secondAttempt).isEqualTo(2);
        assertThat(feedback.getAnalysisStatus()).isEqualTo(FeedbackAnalysisStatus.PROCESSING);
        assertThat(feedback.getPriority()).isNull();
    }

    private Feedback createFeedback() {
        return Feedback.builder()
                .id(1L)
                .user(User.builder().id(1L).steamId("76561198000000001").username("tester").build())
                .category(FeedbackCategory.BUG_REPORT)
                .content("게임 동기화가 멈춥니다.")
                .build();
    }
}
