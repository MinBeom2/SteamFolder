package com.steamfolder.steamfolder.common.monitoring;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordWebhookClientTest {

    private MockWebServer mockWebServer;
    private MonitoringProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        properties = new MonitoringProperties();
        properties.getDiscord().setEnabled(true);
        properties.getDiscord().setWebhookUrl(mockWebServer.url("/webhook").toString());
        properties.getDiscord().setRequestTimeout(Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Discord 웹훅에 JSON 메시지를 전송한다")
    void sendsWebhookPayload() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));
        DiscordWebhookClient client = new DiscordWebhookClient(properties, WebClient.builder());

        boolean sent = client.send(new DiscordWebhookPayload("server down"));

        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(sent).isTrue();
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/webhook");
        assertThat(request.getBody().readUtf8()).contains("server down");
    }

    @Test
    @DisplayName("웹훅이 비활성화되면 외부 요청을 보내지 않는다")
    void skipsRequestWhenDisabled() throws InterruptedException {
        properties.getDiscord().setEnabled(false);
        DiscordWebhookClient client = new DiscordWebhookClient(properties, WebClient.builder());

        boolean sent = client.send(new DiscordWebhookPayload("ignored"));

        assertThat(sent).isFalse();
        assertThat(mockWebServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("Discord 장애가 발생해도 예외를 전파하지 않는다")
    void isolatesWebhookFailure() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        DiscordWebhookClient client = new DiscordWebhookClient(properties, WebClient.builder());

        boolean sent = client.send(new DiscordWebhookPayload("server down"));

        assertThat(sent).isFalse();
    }
}
