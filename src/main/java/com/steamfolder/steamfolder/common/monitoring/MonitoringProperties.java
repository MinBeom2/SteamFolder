package com.steamfolder.steamfolder.common.monitoring;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "monitoring")
public class MonitoringProperties {

    private final Discord discord = new Discord();

    @Getter
    @Setter
    public static class Discord {

        private boolean enabled = false;
        private String webhookUrl = "";
        private String environment = "local";
        private Duration cooldown = Duration.ofMinutes(5);
        private Duration requestTimeout = Duration.ofSeconds(3);
        private int maxMessageLength = 1900;
        private int maxStackTraceLength = 1200;
    }
}
