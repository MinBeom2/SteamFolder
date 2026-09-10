package com.steamfolder.steamfolder.domain.feedback.dto.response;

import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFeedbackResponse {

    private Long feedbackId;
    private LocalDateTime createdAt;

    public static CreateFeedbackResponse from(Feedback feedback) {
        return CreateFeedbackResponse.builder()
                .feedbackId(feedback.getId())
                .createdAt(feedback.getCreatedAt())
                .build();
    }
}
