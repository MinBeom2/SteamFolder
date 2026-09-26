package com.steamfolder.steamfolder.domain.feedback.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.feedback.dto.request.CreateFeedbackRequest;
import com.steamfolder.steamfolder.domain.feedback.dto.response.CreateFeedbackResponse;
import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisStatus;
import com.steamfolder.steamfolder.domain.feedback.event.FeedbackCreatedEvent;
import com.steamfolder.steamfolder.domain.feedback.repository.FeedbackRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final int MIN_CONTENT_LENGTH = 10;
    private static final int MAX_CONTENT_LENGTH = 3000;

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CreateFeedbackResponse createFeedback(Long userId, CreateFeedbackRequest request) {
        User user = userRepository.findById(userId)
                .filter(foundUser -> !foundUser.isDeleted())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (request.getCategory() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String normalizedContent = normalizeContent(request.getContent());

        Feedback feedback = Feedback.builder()
                .user(user)
                .category(request.getCategory())
                .content(normalizedContent)
                .analysisStatus(FeedbackAnalysisStatus.PENDING)
                .build();

        Feedback savedFeedback = feedbackRepository.save(feedback);
        eventPublisher.publishEvent(new FeedbackCreatedEvent(savedFeedback.getId()));
        return CreateFeedbackResponse.from(savedFeedback);
    }

    private String normalizeContent(String content) {
        if (content == null) {
            throw new CustomException(ErrorCode.INVALID_FEEDBACK_CONTENT);
        }

        String normalizedContent = content.trim();
        if (normalizedContent.length() < MIN_CONTENT_LENGTH || normalizedContent.length() > MAX_CONTENT_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_FEEDBACK_CONTENT);
        }
        return normalizedContent;
    }
}
