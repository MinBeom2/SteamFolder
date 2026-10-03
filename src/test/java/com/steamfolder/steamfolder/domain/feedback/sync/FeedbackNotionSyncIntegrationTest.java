package com.steamfolder.steamfolder.domain.feedback.sync;

import com.steamfolder.steamfolder.common.security.jwt.JwtTokenProvider;
import com.steamfolder.steamfolder.domain.feedback.dto.analysis.FeedbackAnalysisResponse;
import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisFailureReason;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackPriority;
import com.steamfolder.steamfolder.domain.feedback.repository.FeedbackRepository;
import com.steamfolder.steamfolder.domain.feedback.service.analysis.FeedbackAnalysisTransactionService;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "feedback.notion-sync.enabled=true",
        "feedback.notion-sync.token=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
@Transactional
class FeedbackNotionSyncIntegrationTest {

    private static final String PATH = "/internal/v1/feedback-notion-sync/jobs";
    private static final String TOKEN = "Bearer 0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackNotionSyncRepository syncRepository;

    @Autowired
    private FeedbackNotionSyncService syncService;

    @Autowired
    private FeedbackAnalysisTransactionService analysisTransactionService;

    @Test
    void acceptsOnlyN8nToken() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        String userJwt = jwtTokenProvider.createAccessToken(1L, "steam-test", "test-user");
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(PATH).header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void exposesCompletedAnalysisAndAcknowledgesOnlyCurrentVersion() throws Exception {
        Feedback feedback = saveFeedback();
        int attempt = feedback.startAnalysis();
        feedbackRepository.flush();
        analysisTransactionService.completeAnalysis(feedback.getId(), attempt,
                new FeedbackAnalysisResponse(FeedbackPriority.P0, "로그인 오류"),
                LocalDateTime.of(2026, 9, 29, 9, 0));

        String response = mockMvc.perform(get(PATH).header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].feedbackId").value(feedback.getId()))
                .andExpect(jsonPath("$.data.items[0].action").value("UPSERT"))
                .andExpect(jsonPath("$.data.items[0].priority").value("P0"))
                .andExpect(jsonPath("$.data.items[0].aiSummary").value("로그인 오류"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(feedback.getContent());
        assertThat(response).doesNotContain(feedback.getUser().getSteamId());

        String ackPath = PATH + "/" + feedback.getId() + "/ack";
        mockMvc.perform(post(ackPath).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":99,\"action\":\"UPSERT\",\"notionPageId\":\"page-1\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post(ackPath).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"action\":\"UPSERT\",\"notionPageId\":\"page-1\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(ackPath).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"action\":\"UPSERT\",\"notionPageId\":\"page-1\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(PATH).header("Authorization", TOKEN))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        analysisTransactionService.markDiscordNotified(feedback.getId(), LocalDateTime.now());
        mockMvc.perform(get(PATH).header("Authorization", TOKEN))
                .andExpect(jsonPath("$.data.items[0].version").value(2))
                .andExpect(jsonPath("$.data.items[0].notionPageId").value("page-1"))
                .andExpect(jsonPath("$.data.items[0].discordNotifiedAt").exists());
        mockMvc.perform(post(ackPath).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"action\":\"UPSERT\",\"notionPageId\":\"page-1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void keepsArchiveJobAfterFeedbackIsDeleted() throws Exception {
        Feedback feedback = saveFeedback();
        syncService.requestUpsert(feedback.getId());
        syncService.requestArchiveForUser(feedback.getUser());
        feedbackRepository.deleteByUser(feedback.getUser());
        feedbackRepository.flush();

        mockMvc.perform(get(PATH).header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].feedbackId").value(feedback.getId()))
                .andExpect(jsonPath("$.data.items[0].action").value("ARCHIVE"))
                .andExpect(jsonPath("$.data.items[0].aiSummary").isEmpty());

        mockMvc.perform(post(PATH + "/" + feedback.getId() + "/ack")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2,\"action\":\"ARCHIVE\"}"))
                .andExpect(status().isNoContent());
        assertThat(syncRepository.findById(feedback.getId()).orElseThrow().getAcknowledgedVersion()).isEqualTo(2);
    }

    @Test
    void seedsExistingCompletedFeedback() throws Exception {
        Feedback feedback = saveFeedback();
        int attempt = feedback.startAnalysis();
        feedback.completeAnalysis(attempt, FeedbackPriority.P1, "폴더 개선", LocalDateTime.now());
        feedbackRepository.flush();

        syncService.seedExisting(3);

        mockMvc.perform(get(PATH).header("Authorization", TOKEN))
                .andExpect(jsonPath("$.data.items[0].feedbackId").value(feedback.getId()))
                .andExpect(jsonPath("$.data.items[0].action").value("UPSERT"));
    }

    @Test
    void exportsOnlyFinalAnalysisFailure() throws Exception {
        Feedback feedback = saveFeedback();
        int attempt = feedback.startAnalysis();
        feedbackRepository.flush();

        analysisTransactionService.failAnalysis(feedback.getId(), attempt,
                FeedbackAnalysisFailureReason.AI_TIMEOUT, 2);
        mockMvc.perform(get(PATH).header("Authorization", TOKEN))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        attempt = feedback.startAnalysis();
        feedbackRepository.flush();
        analysisTransactionService.failAnalysis(feedback.getId(), attempt,
                FeedbackAnalysisFailureReason.AI_TIMEOUT, 2);
        mockMvc.perform(get(PATH).header("Authorization", TOKEN))
                .andExpect(jsonPath("$.data.items[0].analysisStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.items[0].analysisFailureReason").value("AI_TIMEOUT"))
                .andExpect(jsonPath("$.data.items[0].priority").isEmpty());
    }

    @Test
    void paginatesPendingJobsAndRejectsInvalidLimit() throws Exception {
        Feedback first = saveFeedback();
        Feedback second = saveFeedback();
        syncService.requestUpsert(first.getId());
        syncService.requestUpsert(second.getId());

        mockMvc.perform(get(PATH).header("Authorization", TOKEN).param("limit", "1"))
                .andExpect(jsonPath("$.data.items[0].feedbackId").value(first.getId()))
                .andExpect(jsonPath("$.data.nextCursor").value(first.getId()));
        mockMvc.perform(get(PATH).header("Authorization", TOKEN)
                        .param("limit", "1")
                        .param("afterId", first.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].feedbackId").value(second.getId()))
                .andExpect(jsonPath("$.data.nextCursor").isEmpty());
        mockMvc.perform(get(PATH).header("Authorization", TOKEN).param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    private Feedback saveFeedback() {
        User user = userRepository.save(User.builder()
                .steamId("steam-" + System.nanoTime())
                .username("notion-sync-test")
                .build());
        return feedbackRepository.saveAndFlush(Feedback.builder()
                .user(user)
                .category(FeedbackCategory.BUG_REPORT)
                .content("로그인 시 오류가 반복해서 발생합니다.")
                .build());
    }
}
