package com.steamfolder.steamfolder.domain.feedback.config;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "feedback.analysis")
public class FeedbackAnalysisProperties {

    private boolean enabled = false;
    private String openaiUrl = "https://api.openai.com/v1/responses";
    private String openaiApiKey = "";
    private String openaiModel = "gpt-6-luna";
    private Duration requestTimeout = Duration.ofSeconds(30);
    @Min(1)
    private int maxAttempts = 3;
    private long recoveryInitialDelayMs = 30_000;
    private long recoveryIntervalMs = 60_000;
    private Duration staleProcessingAfter = Duration.ofMinutes(5);
    @Min(1)
    private int recoveryBatchSize = 20;
    @Min(1)
    private int maxSummaryLength = 1_000;
    @Min(1)
    private int dailySummaryBatchSize = 10;
    @Min(1)
    private int dailySummaryMaxItems = 100;

    @AssertTrue(message = "feedback analysis requires OpenAI URL, API key and model when enabled")
    public boolean isOpenAiConfigurationValid() {
        return !enabled || (StringUtils.hasText(openaiUrl)
                && StringUtils.hasText(openaiApiKey)
                && StringUtils.hasText(openaiModel));
    }
}
