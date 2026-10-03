package com.steamfolder.steamfolder.domain.feedback.sync;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "feedback.notion-sync")
public class FeedbackNotionSyncProperties {

    private boolean enabled = false;
    private String token = "";

    @AssertTrue(message = "feedback.notion-sync.token must be at least 32 characters when enabled")
    public boolean isTokenValid() {
        return !enabled || (StringUtils.hasText(token) && token.length() >= 32);
    }
}
