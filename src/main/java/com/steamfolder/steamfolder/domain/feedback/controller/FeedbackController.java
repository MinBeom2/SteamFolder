package com.steamfolder.steamfolder.domain.feedback.controller;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.domain.feedback.dto.request.CreateFeedbackRequest;
import com.steamfolder.steamfolder.domain.feedback.dto.response.CreateFeedbackResponse;
import com.steamfolder.steamfolder.domain.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateFeedbackResponse>> createFeedback(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateFeedbackRequest request
    ) {
        CreateFeedbackResponse response = feedbackService.createFeedback(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("피드백이 성공적으로 접수되었습니다.", response));
    }
}
