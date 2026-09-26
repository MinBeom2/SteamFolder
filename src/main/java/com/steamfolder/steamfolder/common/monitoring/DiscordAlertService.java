package com.steamfolder.steamfolder.common.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DiscordAlertService {

    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)(password|secret|token|api[-_ ]?key|authorization)(\\s*[=:]\\s*)([^\\s,;]+)"
    );

    private final MonitoringProperties properties;
    private final DiscordWebhookClient webhookClient;
    private final ConcurrentHashMap<String, Instant> lastSentAt = new ConcurrentHashMap<>();

    @Async("monitoringTaskExecutor")
    public void notifyUnhandledError(Throwable throwable, String method, String path) {
        if (!isEnabled()) {
            return;
        }

        String fingerprint = "ERROR:" + throwable.getClass().getName() + ":" + safe(path);
        if (!reserve(fingerprint)) {
            return;
        }

        String message = "🚨 **SteamFolder 서버 오류**\n"
                + "- 환경: `" + sanitize(properties.getDiscord().getEnvironment()) + "`\n"
                + "- 시각: `" + Instant.now() + "`\n"
                + "- 요청: `" + sanitize(safe(method)) + " " + sanitize(safe(path)) + "`\n"
                + "- 예외: `" + sanitize(throwable.getClass().getSimpleName()) + "`\n"
                + "```\n" + stackTrace(throwable) + "\n```";

        webhookClient.send(new DiscordWebhookPayload(limit(message)));
    }

    @Async("monitoringTaskExecutor")
    public void notifyHealthDown(String previousStatus, String currentStatus, List<String> unhealthyComponents) {
        if (!isEnabled() || !reserve("HEALTH:DOWN:" + currentStatus)) {
            return;
        }

        String components = unhealthyComponents.isEmpty()
                ? "확인 불가"
                : String.join(", ", unhealthyComponents);
        String message = "🔴 **SteamFolder 헬스체크 장애**\n"
                + "- 환경: `" + sanitize(properties.getDiscord().getEnvironment()) + "`\n"
                + "- 시각: `" + Instant.now() + "`\n"
                + "- 상태: `" + sanitize(safe(previousStatus)) + " → " + sanitize(safe(currentStatus)) + "`\n"
                + "- 비정상 구성요소: `" + sanitize(components) + "`";

        webhookClient.send(new DiscordWebhookPayload(limit(message)));
    }

    @Async("monitoringTaskExecutor")
    public void notifyHealthRecovered(String previousStatus, String currentStatus) {
        if (!isEnabled() || !reserve("HEALTH:RECOVERED:" + currentStatus)) {
            return;
        }

        String message = "🟢 **SteamFolder 헬스체크 복구**\n"
                + "- 환경: `" + sanitize(properties.getDiscord().getEnvironment()) + "`\n"
                + "- 시각: `" + Instant.now() + "`\n"
                + "- 상태: `" + sanitize(safe(previousStatus)) + " → " + sanitize(safe(currentStatus)) + "`";

        webhookClient.send(new DiscordWebhookPayload(limit(message)));
    }

    public boolean notifyCriticalFeedback(Long feedbackId, String category, String summary) {
        String fingerprint = "FEEDBACK:P0:" + feedbackId;
        if (!isEnabled() || !reserve(fingerprint)) {
            return false;
        }

        String message = "🆘 **SteamFolder P0 피드백**\n"
                + "- 환경: `" + sanitize(properties.getDiscord().getEnvironment()) + "`\n"
                + "- 시각: `" + Instant.now() + "`\n"
                + "- 피드백 ID: `" + feedbackId + "`\n"
                + "- 카테고리: `" + sanitize(safe(category)) + "`\n"
                + "- AI 요약: " + sanitize(safe(summary));

        return webhookClient.send(new DiscordWebhookPayload(limit(message)));
    }

    public boolean notifyFeedbackDailySummary(String batchKey, List<String> summaryLines) {
        if (summaryLines.isEmpty() || !isEnabled() || !reserve("FEEDBACK:DAILY:" + batchKey)) {
            return false;
        }

        String message = "📬 **SteamFolder 일일 피드백 요약**\n"
                + "- 환경: `" + sanitize(properties.getDiscord().getEnvironment()) + "`\n"
                + "- 시각: `" + Instant.now() + "`\n"
                + sanitize(String.join("\n", summaryLines));

        return webhookClient.send(new DiscordWebhookPayload(limit(message)));
    }

    private boolean isEnabled() {
        return properties.getDiscord().isEnabled();
    }

    private boolean reserve(String fingerprint) {
        Instant now = Instant.now();
        AtomicBoolean reserved = new AtomicBoolean(false);
        lastSentAt.compute(fingerprint, (key, lastSent) -> {
            if (lastSent == null || lastSent.plus(properties.getDiscord().getCooldown()).isBefore(now)) {
                reserved.set(true);
                return now;
            }
            return lastSent;
        });
        return reserved.get();
    }

    private String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        String sanitized = sanitize(writer.toString()).replace("```", "''' ");
        return truncate(sanitized, properties.getDiscord().getMaxStackTraceLength());
    }

    private String limit(String message) {
        return truncate(message, properties.getDiscord().getMaxMessageLength());
    }

    private String truncate(String value, int maxLength) {
        if (maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String sanitize(String value) {
        String withoutBearer = BEARER_TOKEN.matcher(safe(value)).replaceAll("Bearer [REDACTED]");
        return SECRET_VALUE.matcher(withoutBearer).replaceAll("$1$2[REDACTED]");
    }

    private String safe(String value) {
        return value == null ? "unknown" : value;
    }
}
