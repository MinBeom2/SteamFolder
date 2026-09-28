package com.steamfolder.steamfolder.domain.feedback.service.analysis;

import com.steamfolder.steamfolder.domain.feedback.dto.analysis.FeedbackAnalysisResponse;
import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisFailureReason;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisStatus;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackPriority;
import com.steamfolder.steamfolder.domain.feedback.repository.FeedbackRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedbackAnalysisTransactionServiceTest {

    private final FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
    private final FeedbackAnalysisTransactionService service =
            new FeedbackAnalysisTransactionService(feedbackRepository);

    @Test
    @DisplayName("분석 시작 시 PROCESSING으로 변경하고 시도 횟수를 증가시킨다")
    void startsAnalysis() {
        Feedback feedback = feedback();
        when(feedbackRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(feedback));

        Optional<FeedbackAnalysisTarget> target = service.startAnalysis(
                1L,
                3,
                LocalDateTime.now().minusMinutes(5)
        );

        assertThat(target).isPresent();
        assertThat(target.orElseThrow().attempt()).isEqualTo(1);
        assertThat(feedback.getAnalysisStatus()).isEqualTo(FeedbackAnalysisStatus.PROCESSING);
        assertThat(feedback.getAnalysisRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("최대 횟수까지 처리 중이던 작업이 멈추면 FAILED와 AI_TIMEOUT으로 종료한다")
    void failsExhaustedStaleProcessing() {
        Feedback feedback = Feedback.builder()
                .id(1L)
                .user(user())
                .category(FeedbackCategory.BUG_REPORT)
                .content("게임 동기화가 멈춥니다.")
                .analysisStatus(FeedbackAnalysisStatus.PROCESSING)
                .analysisRetryCount(3)
                .build();
        ReflectionTestUtils.setField(feedback, "updatedAt", LocalDateTime.now().minusMinutes(10));
        when(feedbackRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(feedback));

        Optional<FeedbackAnalysisTarget> target = service.startAnalysis(
                1L,
                3,
                LocalDateTime.now().minusMinutes(5)
        );

        assertThat(target).isEmpty();
        assertThat(feedback.getAnalysisStatus()).isEqualTo(FeedbackAnalysisStatus.FAILED);
        assertThat(feedback.getAnalysisFailureReason())
                .isEqualTo(FeedbackAnalysisFailureReason.AI_TIMEOUT);
    }

    @Test
    @DisplayName("현재 시도 번호가 일치할 때만 분석을 완료한다")
    void completesOnlyCurrentAttempt() {
        Feedback feedback = feedback();
        int attempt = feedback.startAnalysis();
        when(feedbackRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(feedback));
        FeedbackAnalysisResponse response = new FeedbackAnalysisResponse(FeedbackPriority.P1, "개선 요청");

        boolean staleCompleted = service.completeAnalysis(1L, attempt + 1, response, LocalDateTime.now());
        boolean currentCompleted = service.completeAnalysis(1L, attempt, response, LocalDateTime.now());

        assertThat(staleCompleted).isFalse();
        assertThat(currentCompleted).isTrue();
        assertThat(feedback.getAnalysisStatus()).isEqualTo(FeedbackAnalysisStatus.COMPLETED);
    }

    private Feedback feedback() {
        return Feedback.builder()
                .id(1L)
                .user(user())
                .category(FeedbackCategory.BUG_REPORT)
                .content("게임 동기화가 멈춥니다.")
                .build();
    }

    private User user() {
        return User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("tester")
                .build();
    }
}
