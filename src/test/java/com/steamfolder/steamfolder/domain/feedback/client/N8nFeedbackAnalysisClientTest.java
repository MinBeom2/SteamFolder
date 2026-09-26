package com.steamfolder.steamfolder.domain.feedback.client;

import com.steamfolder.steamfolder.domain.feedback.config.FeedbackAnalysisProperties;
import com.steamfolder.steamfolder.domain.feedback.dto.analysis.FeedbackAnalysisRequest;
import com.steamfolder.steamfolder.domain.feedback.dto.analysis.FeedbackAnalysisResponse;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisFailureReason;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackPriority;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class N8nFeedbackAnalysisClientTest {

    private MockWebServer mockWebServer;
    private FeedbackAnalysisProperties properties;
    private N8nFeedbackAnalysisClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        properties = new FeedbackAnalysisProperties();
        properties.setEnabled(true);
        properties.setWebhookUrl(mockWebServer.url("/feedback-analysis").toString());
        properties.setWebhookSecret("test-secret");
        properties.setRequestTimeout(Duration.ofSeconds(5));
        client = new N8nFeedbackAnalysisClient(properties, WebClient.builder());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("n8n 분석 결과와 Notion 중복 방지 키인 feedbackId를 주고받는다")
    void analyzesFeedback() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"priority\":\"P0\",\"summary\":\"  긴급 동기화 장애  \"}"));

        FeedbackAnalysisResponse response = client.analyze(request());

        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(response.priority()).isEqualTo(FeedbackPriority.P0);
        assertThat(response.summary()).isEqualTo("긴급 동기화 장애");
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getHeader(N8nFeedbackAnalysisClient.WEBHOOK_SECRET_HEADER))
                .isEqualTo("test-secret");
        assertThat(recordedRequest.getBody().readUtf8())
                .contains("\"feedbackId\":102")
                .contains("\"category\":\"BUG_REPORT\"");
    }

    @Test
    @DisplayName("필수 분석 결과가 없으면 AI_INVALID_RESPONSE로 처리한다")
    void rejectsInvalidResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"priority\":\"P1\",\"summary\":\"   \"}"));

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOf(FeedbackAnalysisException.class)
                .hasFieldOrPropertyWithValue(
                        "failureReason",
                        FeedbackAnalysisFailureReason.AI_INVALID_RESPONSE
                );
    }

    @Test
    @DisplayName("n8n 서버 오류는 AI_ANALYSIS_FAILED로 처리한다")
    void mapsServerError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOf(FeedbackAnalysisException.class)
                .hasFieldOrPropertyWithValue(
                        "failureReason",
                        FeedbackAnalysisFailureReason.AI_ANALYSIS_FAILED
                );
    }

    @Test
    @DisplayName("분석 설정이 비활성화되면 외부 요청 없이 설정 오류를 반환한다")
    void rejectsDisabledConfiguration() throws InterruptedException {
        properties.setEnabled(false);

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOf(FeedbackAnalysisException.class)
                .hasFieldOrPropertyWithValue(
                        "failureReason",
                        FeedbackAnalysisFailureReason.N8N_CONFIGURATION_MISSING
                );
        assertThat(mockWebServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    private FeedbackAnalysisRequest request() {
        return new FeedbackAnalysisRequest(
                102L,
                FeedbackCategory.BUG_REPORT,
                "게임 동기화가 멈춥니다.",
                LocalDateTime.of(2026, 9, 21, 9, 0)
        );
    }
}
