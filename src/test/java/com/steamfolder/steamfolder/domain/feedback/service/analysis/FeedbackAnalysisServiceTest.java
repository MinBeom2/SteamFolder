package com.steamfolder.steamfolder.domain.feedback.service.analysis;

import com.steamfolder.steamfolder.common.monitoring.DiscordAlertService;
import com.steamfolder.steamfolder.domain.feedback.client.FeedbackAnalysisException;
import com.steamfolder.steamfolder.domain.feedback.client.N8nFeedbackAnalysisClient;
import com.steamfolder.steamfolder.domain.feedback.config.FeedbackAnalysisProperties;
import com.steamfolder.steamfolder.domain.feedback.dto.analysis.FeedbackAnalysisResponse;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisFailureReason;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackAnalysisServiceTest {

    private FeedbackAnalysisProperties properties;
    private FeedbackAnalysisTransactionService transactionService;
    private N8nFeedbackAnalysisClient analysisClient;
    private DiscordAlertService discordAlertService;
    private FeedbackAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        properties = new FeedbackAnalysisProperties();
        properties.setEnabled(true);
        transactionService = mock(FeedbackAnalysisTransactionService.class);
        analysisClient = mock(N8nFeedbackAnalysisClient.class);
        discordAlertService = mock(DiscordAlertService.class);
        analysisService = new FeedbackAnalysisService(
                properties,
                transactionService,
                analysisClient,
                discordAlertService
        );
    }

    @Test
    @DisplayName("P0 분석 완료와 Discord 전송 성공 시 알림 완료 시각을 기록한다")
    void completesP0AnalysisAndMarksDiscordNotification() {
        FeedbackAnalysisTarget target = target();
        FeedbackAnalysisResponse response = new FeedbackAnalysisResponse(FeedbackPriority.P0, "긴급 장애");
        when(transactionService.startAnalysis(eq(102L), eq(3), any())).thenReturn(Optional.of(target));
        when(analysisClient.analyze(any())).thenReturn(response);
        when(transactionService.completeAnalysis(eq(102L), eq(1), eq(response), any())).thenReturn(true);
        when(transactionService.findCriticalAlertTarget(102L)).thenReturn(Optional.of(
                new FeedbackCriticalAlertTarget(102L, FeedbackCategory.BUG_REPORT, "긴급 장애")
        ));
        when(discordAlertService.notifyCriticalFeedback(102L, "BUG_REPORT", "긴급 장애"))
                .thenReturn(true);

        analysisService.analyzeAsync(102L);

        verify(transactionService).markDiscordNotified(eq(102L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("P1 분석은 완료하지만 즉시 Discord 알림을 보내지 않는다")
    void completesP1WithoutImmediateDiscordNotification() {
        FeedbackAnalysisTarget target = target();
        FeedbackAnalysisResponse response = new FeedbackAnalysisResponse(FeedbackPriority.P1, "중요 개선 요청");
        when(transactionService.startAnalysis(eq(102L), eq(3), any())).thenReturn(Optional.of(target));
        when(analysisClient.analyze(any())).thenReturn(response);
        when(transactionService.completeAnalysis(eq(102L), eq(1), eq(response), any())).thenReturn(true);

        analysisService.analyzeAsync(102L);

        verify(discordAlertService, never()).notifyCriticalFeedback(any(), any(), any());
    }

    @Test
    @DisplayName("n8n 분석 실패 시 원인 코드를 저장하고 피드백 제출 결과에는 영향을 주지 않는다")
    void storesAnalysisFailureReason() {
        FeedbackAnalysisTarget target = target();
        when(transactionService.startAnalysis(eq(102L), eq(3), any())).thenReturn(Optional.of(target));
        when(analysisClient.analyze(any())).thenThrow(new FeedbackAnalysisException(
                FeedbackAnalysisFailureReason.N8N_TIMEOUT,
                "timeout"
        ));

        analysisService.analyzeAsync(102L);

        verify(transactionService).failAnalysis(
                102L,
                1,
                FeedbackAnalysisFailureReason.N8N_TIMEOUT
        );
        verify(discordAlertService, never()).notifyCriticalFeedback(any(), any(), any());
    }

    @Test
    @DisplayName("P1과 P2 피드백은 중요도순 일일 요약 전송 후 알림 시각을 기록한다")
    void sendsDailySummaryAndMarksItems() {
        List<FeedbackDailySummaryItem> items = List.of(
                new FeedbackDailySummaryItem(201L, FeedbackPriority.P1, FeedbackCategory.BUG_REPORT, "중요 오류"),
                new FeedbackDailySummaryItem(202L, FeedbackPriority.P2, FeedbackCategory.GENERAL, "일반 의견")
        );
        when(transactionService.findPendingDailySummaryItems(10))
                .thenReturn(items)
                .thenReturn(List.of());
        when(discordAlertService.notifyFeedbackDailySummary(
                eq("201-202"),
                eq(List.of("[P1] #201 BUG_REPORT - 중요 오류", "[P2] #202 GENERAL - 일반 의견"))
        )).thenReturn(true);

        analysisService.sendDailySummaryAsync();

        verify(transactionService).markDiscordNotified(eq(List.of(201L, 202L)), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("일일 요약 전송 실패 시 Discord 알림 시각을 기록하지 않는다")
    void keepsDailySummaryPendingWhenDiscordFails() {
        List<FeedbackDailySummaryItem> items = List.of(
                new FeedbackDailySummaryItem(201L, FeedbackPriority.P1, FeedbackCategory.BUG_REPORT, "중요 오류")
        );
        when(transactionService.findPendingDailySummaryItems(10)).thenReturn(items);
        when(discordAlertService.notifyFeedbackDailySummary(any(), any())).thenReturn(false);

        analysisService.sendDailySummaryAsync();

        verify(transactionService, never()).markDiscordNotified(
                org.mockito.ArgumentMatchers.<List<Long>>any(),
                any(LocalDateTime.class)
        );
    }

    private FeedbackAnalysisTarget target() {
        return new FeedbackAnalysisTarget(
                102L,
                FeedbackCategory.BUG_REPORT,
                "게임 동기화가 멈춥니다.",
                LocalDateTime.of(2026, 9, 21, 9, 0),
                1
        );
    }
}
