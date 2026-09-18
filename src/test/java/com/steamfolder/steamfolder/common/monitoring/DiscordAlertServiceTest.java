package com.steamfolder.steamfolder.common.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DiscordAlertServiceTest {

    private MonitoringProperties properties;
    private DiscordWebhookClient webhookClient;
    private DiscordAlertService alertService;

    @BeforeEach
    void setUp() {
        properties = new MonitoringProperties();
        properties.getDiscord().setEnabled(true);
        properties.getDiscord().setEnvironment("test");
        properties.getDiscord().setCooldown(Duration.ofMinutes(5));
        webhookClient = mock(DiscordWebhookClient.class);
        alertService = new DiscordAlertService(properties, webhookClient);
    }

    @Test
    @DisplayName("같은 종류의 서버 오류는 쿨다운 동안 한 번만 알린다")
    void suppressesDuplicateErrorDuringCooldown() {
        RuntimeException exception = new RuntimeException("token=very-secret");

        alertService.notifyUnhandledError(exception, "GET", "/api/v1/test");
        alertService.notifyUnhandledError(exception, "GET", "/api/v1/test");

        ArgumentCaptor<DiscordWebhookPayload> payloadCaptor = ArgumentCaptor.forClass(DiscordWebhookPayload.class);
        verify(webhookClient, times(1)).send(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().content())
                .contains("GET /api/v1/test")
                .contains("token=[REDACTED]")
                .doesNotContain("very-secret");
    }

    @Test
    @DisplayName("오류가 발생한 경로가 다르면 각각 알린다")
    void sendsAlertsForDifferentPaths() {
        RuntimeException exception = new RuntimeException("failure");

        alertService.notifyUnhandledError(exception, "GET", "/api/v1/first");
        alertService.notifyUnhandledError(exception, "GET", "/api/v1/second");

        verify(webhookClient, times(2)).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Discord 제한에 맞게 긴 오류 메시지를 잘라낸다")
    void truncatesLongMessages() {
        properties.getDiscord().setMaxMessageLength(120);
        properties.getDiscord().setMaxStackTraceLength(80);

        alertService.notifyUnhandledError(
                new RuntimeException("x".repeat(500)),
                "POST",
                "/api/v1/test"
        );

        ArgumentCaptor<DiscordWebhookPayload> payloadCaptor = ArgumentCaptor.forClass(DiscordWebhookPayload.class);
        verify(webhookClient).send(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().content()).hasSizeLessThanOrEqualTo(120);
    }

    @Test
    @DisplayName("Discord 알림이 비활성화되면 메시지를 만들거나 전송하지 않는다")
    void skipsAlertsWhenDisabled() {
        properties.getDiscord().setEnabled(false);

        alertService.notifyUnhandledError(new RuntimeException("failure"), "GET", "/api/v1/test");

        verifyNoInteractions(webhookClient);
    }
}
