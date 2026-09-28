package com.steamfolder.steamfolder.domain.feedback.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiFeedbackAnalysisClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer mockWebServer;
    private FeedbackAnalysisProperties properties;
    private OpenAiFeedbackAnalysisClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        properties = new FeedbackAnalysisProperties();
        properties.setEnabled(true);
        properties.setOpenaiUrl(mockWebServer.url("/v1/responses").toString());
        properties.setOpenaiApiKey("test-api-key");
        properties.setRequestTimeout(Duration.ofSeconds(5));
        client = new OpenAiFeedbackAnalysisClient(properties, WebClient.builder(), objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("OpenAI 구조화 응답을 분석하고 피드백 내용만 API에 전송한다")
    void analyzesFeedback() throws Exception {
        mockWebServer.enqueue(jsonResponse(completedResponse(
                "{\"priority\":\"P0\",\"summary\":\"  긴급 동기화 장애  \"}")
        ));

        FeedbackAnalysisResponse response = client.analyze(request());

        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(response.priority()).isEqualTo(FeedbackPriority.P0);
        assertThat(response.summary()).isEqualTo("긴급 동기화 장애");
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).isEqualTo("/v1/responses");
        assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-api-key");

        JsonNode body = objectMapper.readTree(recordedRequest.getBody().readUtf8());
        assertThat(body.path("model").asText()).isEqualTo("gpt-6-luna");
        assertThat(body.path("store").asBoolean()).isFalse();
        assertThat(body.path("reasoning").path("effort").asText()).isEqualTo("low");
        assertThat(body.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(body.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(body.path("input").asText())
                .contains("BUG_REPORT", "게임 동기화가 멈춥니다.")
                .doesNotContain("102", "2026-09-21");
    }

    @Test
    @DisplayName("완료되지 않은 응답은 분석 실패로 기록한다")
    void rejectsIncompleteResponse() {
        mockWebServer.enqueue(jsonResponse("{\"status\":\"incomplete\",\"output\":[]}"));

        assertFailureReason(FeedbackAnalysisFailureReason.AI_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("거절 응답은 분석 결과로 저장하지 않는다")
    void rejectsRefusal() throws JsonProcessingException {
        String response = objectMapper.writeValueAsString(Map.of(
                "status", "completed",
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(Map.of("type", "refusal", "refusal", "Cannot analyze"))
                ))
        ));
        mockWebServer.enqueue(jsonResponse(response));

        assertFailureReason(FeedbackAnalysisFailureReason.AI_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("요약이 비어 있으면 분석 결과로 저장하지 않는다")
    void rejectsEmptySummary() throws JsonProcessingException {
        mockWebServer.enqueue(jsonResponse(completedResponse(
                "{\"priority\":\"P1\",\"summary\":\"   \"}"
        )));

        assertFailureReason(FeedbackAnalysisFailureReason.AI_INVALID_RESPONSE);
    }

    @ParameterizedTest
    @CsvSource({
            "401,AI_AUTHENTICATION_FAILED",
            "429,AI_RATE_LIMITED",
            "500,AI_ANALYSIS_FAILED"
    })
    @DisplayName("HTTP 오류를 비밀 정보 없이 실패 사유로 변환한다")
    void mapsHttpErrors(int status, FeedbackAnalysisFailureReason expectedReason) {
        mockWebServer.enqueue(new MockResponse().setResponseCode(status));

        assertFailureReason(expectedReason);
    }

    @Test
    @DisplayName("응답 시간이 초과되면 AI_TIMEOUT으로 처리한다")
    void mapsTimeout() {
        properties.setRequestTimeout(Duration.ofMillis(50));
        mockWebServer.enqueue(jsonResponse("{}").setBodyDelay(1, TimeUnit.SECONDS));

        assertFailureReason(FeedbackAnalysisFailureReason.AI_TIMEOUT);
    }

    @Test
    @DisplayName("API 키가 없으면 외부 요청을 보내지 않는다")
    void rejectsMissingConfiguration() throws InterruptedException {
        properties.setOpenaiApiKey("");

        assertFailureReason(FeedbackAnalysisFailureReason.AI_CONFIGURATION_MISSING);
        assertThat(mockWebServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("피드백 분석이 비활성화되면 외부 요청을 보내지 않는다")
    void rejectsDisabledAnalysis() throws InterruptedException {
        properties.setEnabled(false);

        assertFailureReason(FeedbackAnalysisFailureReason.AI_CONFIGURATION_MISSING);
        assertThat(mockWebServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    private void assertFailureReason(FeedbackAnalysisFailureReason expectedReason) {
        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOf(FeedbackAnalysisException.class)
                .hasFieldOrPropertyWithValue("failureReason", expectedReason);
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }

    private String completedResponse(String outputText) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "status", "completed",
                "output", List.of(
                        Map.of("type", "reasoning", "summary", List.of()),
                        Map.of("type", "message", "content", List.of(Map.of(
                                "type", "output_text",
                                "text", outputText
                        )))
                )
        ));
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
