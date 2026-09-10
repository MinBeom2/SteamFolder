package com.steamfolder.steamfolder.domain.feedback.dto.request;

import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CreateFeedbackRequest {

    @NotNull(message = "피드백 카테고리는 필수 입력값입니다.")
    private FeedbackCategory category;

    @NotBlank(message = "피드백 내용은 필수 입력값입니다.")
    @Size(min = 10, max = 3000, message = "피드백 내용은 10자 이상 3,000자 이하여야 합니다.")
    private String content;
}
