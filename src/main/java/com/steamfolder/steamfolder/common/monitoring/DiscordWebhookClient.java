package com.steamfolder.steamfolder.common.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class DiscordWebhookClient {

    private final MonitoringProperties properties;
    private final WebClient webClient;

    public DiscordWebhookClient(MonitoringProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.build();
    }

    public boolean send(DiscordWebhookPayload payload) {
        MonitoringProperties.Discord discord = properties.getDiscord();
        if (!discord.isEnabled() || !StringUtils.hasText(discord.getWebhookUrl())) {
            return false;
        }

        try {
            webClient.post()
                    .uri(discord.getWebhookUrl())
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(discord.getRequestTimeout())
                    .block();
            return true;
        } catch (Exception exception) {
            log.warn("Discord 모니터링 알림 전송에 실패했습니다: {}", exception.getMessage());
            return false;
        }
    }
}
