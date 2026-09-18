package com.steamfolder.steamfolder.common.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemHealthMonitorTest {

    private HealthEndpoint healthEndpoint;
    private DiscordAlertService alertService;
    private SystemHealthMonitor monitor;

    @BeforeEach
    void setUp() {
        healthEndpoint = mock(HealthEndpoint.class);
        alertService = mock(DiscordAlertService.class);
        monitor = new SystemHealthMonitor(healthEndpoint, alertService);
    }

    @Test
    @DisplayName("UP에서 DOWN으로 바뀔 때 한 번 알리고 DOWN이 유지되면 반복 알림하지 않는다")
    void alertsOnlyOnDownTransition() {
        when(healthEndpoint.health())
                .thenReturn(Health.up().build())
                .thenReturn(Health.down().build())
                .thenReturn(Health.down().build());

        monitor.checkHealth();
        monitor.checkHealth();
        monitor.checkHealth();

        verify(alertService).notifyHealthDown("UP", "DOWN", java.util.List.of("system=DOWN"));
        verify(alertService, never()).notifyHealthRecovered(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("DOWN 상태에서 UP으로 복구되면 한 번 알린다")
    void alertsOnRecoveryTransition() {
        when(healthEndpoint.health())
                .thenReturn(Health.down().build())
                .thenReturn(Health.up().build())
                .thenReturn(Health.up().build());

        monitor.checkHealth();
        monitor.checkHealth();
        monitor.checkHealth();

        verify(alertService).notifyHealthDown("UNKNOWN", "DOWN", java.util.List.of("system=DOWN"));
        verify(alertService).notifyHealthRecovered("DOWN", "UP");
    }
}
