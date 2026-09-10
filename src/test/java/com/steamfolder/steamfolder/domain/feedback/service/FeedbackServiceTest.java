package com.steamfolder.steamfolder.domain.feedback.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.feedback.dto.request.CreateFeedbackRequest;
import com.steamfolder.steamfolder.domain.feedback.dto.response.CreateFeedbackResponse;
import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisStatus;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;
import com.steamfolder.steamfolder.domain.feedback.repository.FeedbackRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @InjectMocks
    private FeedbackService feedbackService;

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("피드백 제출 시 내용을 정규화하고 PENDING 상태로 저장")
    void createFeedback_Success() {
        // given
        User user = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("FeedbackUser")
                .build();
        CreateFeedbackRequest request = CreateFeedbackRequest.builder()
                .category(FeedbackCategory.BUG_REPORT)
                .content("  스팀 동기화 진행률이 멈춥니다.  ")
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(feedbackRepository.save(any(Feedback.class))).willAnswer(invocation -> {
            Feedback feedback = invocation.getArgument(0);
            return Feedback.builder()
                    .id(102L)
                    .user(feedback.getUser())
                    .category(feedback.getCategory())
                    .content(feedback.getContent())
                    .analysisStatus(feedback.getAnalysisStatus())
                    .build();
        });

        // when
        CreateFeedbackResponse response = feedbackService.createFeedback(1L, request);

        // then
        ArgumentCaptor<Feedback> feedbackCaptor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(feedbackCaptor.capture());

        Feedback savedFeedback = feedbackCaptor.getValue();
        assertThat(response.getFeedbackId()).isEqualTo(102L);
        assertThat(savedFeedback.getUser()).isEqualTo(user);
        assertThat(savedFeedback.getCategory()).isEqualTo(FeedbackCategory.BUG_REPORT);
        assertThat(savedFeedback.getContent()).isEqualTo("스팀 동기화 진행률이 멈춥니다.");
        assertThat(savedFeedback.getAnalysisStatus()).isEqualTo(FeedbackAnalysisStatus.PENDING);
    }

    @Test
    @DisplayName("공백 제거 후 10자 미만인 피드백은 저장하지 않음")
    void createFeedback_TooShortAfterTrim_ThrowsException() {
        // given
        User user = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("FeedbackUser")
                .build();
        CreateFeedbackRequest request = CreateFeedbackRequest.builder()
                .category(FeedbackCategory.GENERAL)
                .content("     123456789     ")
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> feedbackService.createFeedback(1L, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FEEDBACK_CONTENT);
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 피드백은 저장하지 않음")
    void createFeedback_UserNotFound_ThrowsException() {
        // given
        CreateFeedbackRequest request = CreateFeedbackRequest.builder()
                .category(FeedbackCategory.FEATURE_REQUEST)
                .content("새로운 폴더 정렬 기능을 추가해주세요.")
                .build();
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> feedbackService.createFeedback(999L, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("탈퇴한 사용자의 피드백은 저장하지 않음")
    void createFeedback_DeletedUser_ThrowsException() {
        // given
        User user = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("DeletedUser")
                .build();
        user.softDelete();

        CreateFeedbackRequest request = CreateFeedbackRequest.builder()
                .category(FeedbackCategory.GENERAL)
                .content("서비스에 대한 일반적인 피드백입니다.")
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> feedbackService.createFeedback(1L, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        verify(feedbackRepository, never()).save(any());
    }
}
