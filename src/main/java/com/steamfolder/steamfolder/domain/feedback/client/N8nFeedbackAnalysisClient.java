package com.steamfolder.steamfolder.domain.feedback.client;

import com.steamfolder.steamfolder.domain.feedback.config.FeedbackAnalysisProperties;
import com.steamfolder.steamfolder.domain.feedback.dto.analysis.FeedbackAnalysisRequest;
import com.steamfolder.steamfolder.domain.feedback.dto.analysis.FeedbackAnalysisResponse;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisFailureReason;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.concurrent.TimeoutException;

@Component
public class N8nFeedbackAnalysisClient {

    static final String WEBHOOK_SECRET_HEADER = "X-Webhook-Secret";

    private final FeedbackAnalysisProperties properties;
    private final WebClient webClient;

    public N8nFeedbackAnalysisClient(
            FeedbackAnalysisProperties properties,
            WebClient.Builder webClientBuilder
    ) {
        this.properties = properties;
        this.webClient = webClientBuilder.build();
    }

    public FeedbackAnalysisResponse analyze(FeedbackAnalysisRequest request) {
        validateConfiguration();

        try {
            FeedbackAnalysisResponse response = webClient.post()
                    .uri(properties.getWebhookUrl())
                    .headers(headers -> addSecretHeader(headers, properties.getWebhookSecret()))
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(FeedbackAnalysisResponse.class)
                    .timeout(properties.getRequestTimeout())
                    .block();

            return validateResponse(response);
        } catch (FeedbackAnalysisException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            throw new FeedbackAnalysisException(
                    FeedbackAnalysisFailureReason.AI_ANALYSIS_FAILED,
                    "n8n feedback analysis returned HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (WebClientRequestException exception) {
            throw new FeedbackAnalysisException(
                    FeedbackAnalysisFailureReason.N8N_CONNECTION_FAILED,
                    "Could not connect to n8n feedback analysis webhook",
                    exception
            );
        } catch (RuntimeException exception) {
            if (hasCause(exception, TimeoutException.class)) {
                throw new FeedbackAnalysisException(
                        FeedbackAnalysisFailureReason.N8N_TIMEOUT,
                        "n8n feedback analysis timed out",
                        exception
                );
            }
            throw new FeedbackAnalysisException(
                    FeedbackAnalysisFailureReason.AI_INVALID_RESPONSE,
                    "n8n feedback analysis returned an invalid response",
                    exception
            );
        }
    }

    private void validateConfiguration() {
        if (!properties.isEnabled()
                || !StringUtils.hasText(properties.getWebhookUrl())
                || !StringUtils.hasText(properties.getWebhookSecret())) {
            throw new FeedbackAnalysisException(
                    FeedbackAnalysisFailureReason.N8N_CONFIGURATION_MISSING,
                    "n8n feedback analysis is disabled or webhook configuration is missing"
            );
        }
    }

    private FeedbackAnalysisResponse validateResponse(FeedbackAnalysisResponse response) {
        if (response == null || response.priority() == null || !StringUtils.hasText(response.summary())) {
            throw new FeedbackAnalysisException(
                    FeedbackAnalysisFailureReason.AI_INVALID_RESPONSE,
                    "n8n feedback analysis response must contain priority and summary"
            );
        }

        String normalizedSummary = response.summary().trim();
        if (normalizedSummary.length() > properties.getMaxSummaryLength()) {
            throw new FeedbackAnalysisException(
                    FeedbackAnalysisFailureReason.AI_INVALID_RESPONSE,
                    "n8n feedback analysis summary exceeds the configured maximum length"
            );
        }
        return new FeedbackAnalysisResponse(response.priority(), normalizedSummary);
    }

    private void addSecretHeader(HttpHeaders headers, String secret) {
        if (StringUtils.hasText(secret)) {
            headers.set(WEBHOOK_SECRET_HEADER, secret);
        }
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
