package com.steamfolder.steamfolder.common.monitoring;

import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ConditionalOnProperty(prefix = "monitoring.health", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SystemHealthMonitor {

    private final HealthEndpoint healthEndpoint;
    private final DiscordAlertService alertService;
    private final AtomicReference<Status> previousStatus = new AtomicReference<>();

    public SystemHealthMonitor(HealthEndpoint healthEndpoint, DiscordAlertService alertService) {
        this.healthEndpoint = healthEndpoint;
        this.alertService = alertService;
    }

    @Scheduled(
            initialDelayString = "${monitoring.health.initial-delay-ms:30000}",
            fixedDelayString = "${monitoring.health.check-interval-ms:60000}"
    )
    public void checkHealth() {
        HealthComponent health = healthEndpoint.health();
        Status current = health.getStatus();
        Status previous = previousStatus.getAndSet(current);

        if (isUp(current)) {
            if (previous != null && !isUp(previous)) {
                alertService.notifyHealthRecovered(previous.getCode(), current.getCode());
            }
            return;
        }

        if (previous == null || isUp(previous)) {
            List<String> unhealthyComponents = new ArrayList<>();
            collectUnhealthyComponents("system", health, unhealthyComponents);
            alertService.notifyHealthDown(
                    previous == null ? "UNKNOWN" : previous.getCode(),
                    current.getCode(),
                    unhealthyComponents
            );
        }
    }

    private boolean isUp(Status status) {
        return Status.UP.equals(status);
    }

    private void collectUnhealthyComponents(
            String path,
            HealthComponent component,
            List<String> unhealthyComponents
    ) {
        if (component instanceof CompositeHealth compositeHealth) {
            for (Map.Entry<String, HealthComponent> entry : compositeHealth.getComponents().entrySet()) {
                collectUnhealthyComponents(path + "." + entry.getKey(), entry.getValue(), unhealthyComponents);
            }
            return;
        }

        if (!isUp(component.getStatus())) {
            unhealthyComponents.add(path + "=" + component.getStatus().getCode());
        }
    }
}
