package com.steamfolder.steamfolder.domain.feedback.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.steamfolder.steamfolder.common.exception.GlobalExceptionHandler;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.common.security.config.SecurityConfig;
import com.steamfolder.steamfolder.common.security.jwt.JwtAuthenticationEntryPoint;
import com.steamfolder.steamfolder.common.security.jwt.JwtTokenProvider;
import com.steamfolder.steamfolder.domain.feedback.dto.request.CreateFeedbackRequest;
import com.steamfolder.steamfolder.domain.feedback.dto.response.CreateFeedbackResponse;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;
import com.steamfolder.steamfolder.domain.feedback.service.FeedbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeedbackController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationEntryPoint.class})
class FeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FeedbackService feedbackService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private UsernamePasswordAuthenticationToken testAuth;

    @BeforeEach
    void setUp() {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(1L)
                .steamId("76561198000000001")
                .username("FeedbackUser")
                .build();
        testAuth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("POST /api/v1/feedbacks - 인증 사용자의 피드백 제출 성공 201 Created")
    void createFeedback_Authenticated_Returns201() throws Exception {
        // given
        CreateFeedbackRequest request = CreateFeedbackRequest.builder()
                .category(FeedbackCategory.BUG_REPORT)
                .content("스팀 동기화 진행률이 멈춥니다.")
                .build();
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 10, 9, 30);
        CreateFeedbackResponse response = CreateFeedbackResponse.builder()
                .feedbackId(102L)
                .createdAt(createdAt)
                .build();
        given(feedbackService.createFeedback(eq(1L), any(CreateFeedbackRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/feedbacks")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("피드백이 성공적으로 접수되었습니다."))
                .andExpect(jsonPath("$.data.feedbackId").value(102L))
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-10T09:30:00"));

        verify(feedbackService).createFeedback(eq(1L), any(CreateFeedbackRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/feedbacks - 인증 없이 요청하면 401 Unauthorized")
    void createFeedback_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/feedbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"GENERAL\",\"content\":\"서비스에 대한 일반적인 피드백입니다.\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("POST /api/v1/feedbacks - 10자 미만 내용은 400 Bad Request")
    void createFeedback_TooShort_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/feedbacks")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"GENERAL\",\"content\":\"짧은 내용\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("content"));
    }

    @Test
    @DisplayName("POST /api/v1/feedbacks - 카테고리 누락은 400 Bad Request")
    void createFeedback_MissingCategory_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/feedbacks")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"서비스에 대한 일반적인 피드백입니다.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("POST /api/v1/feedbacks - 지원하지 않는 카테고리는 400 Bad Request")
    void createFeedback_InvalidCategory_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/feedbacks")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"UNKNOWN\",\"content\":\"서비스에 대한 일반적인 피드백입니다.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }
}
