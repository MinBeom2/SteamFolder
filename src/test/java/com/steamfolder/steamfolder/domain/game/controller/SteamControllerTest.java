package com.steamfolder.steamfolder.domain.game.controller;

import com.steamfolder.steamfolder.common.exception.GlobalExceptionHandler;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.common.security.config.SecurityConfig;
import com.steamfolder.steamfolder.common.security.jwt.JwtAuthenticationEntryPoint;
import com.steamfolder.steamfolder.common.security.jwt.JwtTokenProvider;
import com.steamfolder.steamfolder.domain.game.dto.response.CooldownResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.SyncResultResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.UserGameResponseDto;
import com.steamfolder.steamfolder.domain.game.service.SteamSyncService;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SteamController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SteamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SteamSyncService steamSyncService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UserPrincipal testPrincipal;
    private UsernamePasswordAuthenticationToken testAuth;
    private User testUser;

    @BeforeEach
    void setUp() {
        testPrincipal = UserPrincipal.builder()
                .userId(1L)
                .steamId("76561198000000001")
                .username("SyncUser")
                .build();
        testAuth = new UsernamePasswordAuthenticationToken(testPrincipal, null, testPrincipal.getAuthorities());

        testUser = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("SyncUser")
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
    }

    @Test
    @DisplayName("POST /api/v1/steam/sync - 게임 라이브러리 동기화 200 OK (요청 본문 없음)")
    void syncOwnedGames_Returns200() throws Exception {
        // given
        SyncResultResponseDto response = SyncResultResponseDto.builder()
                .syncedGameCount(10)
                .newlyAddedCount(2)
                .lastSyncedAt(LocalDateTime.now())
                .build();

        given(steamSyncService.syncUserGames(eq(testUser))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/steam/sync")
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.syncedGameCount").value(10))
                .andExpect(jsonPath("$.data.newlyAddedCount").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/steam/sync/cooldown - 동기화 쿨다운 상태 조회 200 OK")
    void getSyncCooldown_Returns200() throws Exception {
        // given
        CooldownResponseDto response = CooldownResponseDto.builder()
                .isCooldownActive(true)
                .remainingSeconds(180)
                .lastSyncedAt(LocalDateTime.now())
                .build();

        given(steamSyncService.getCooldownStatus(eq(testUser))).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/steam/sync/cooldown")
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cooldownActive").value(true))
                .andExpect(jsonPath("$.data.remainingSeconds").value(180));
    }

    @Test
    @DisplayName("GET /api/v1/steam/user-games - 로컬 동기화 게임 목록 조회 200 OK")
    void getUserGames_Returns200() throws Exception {
        // given
        UserGameResponseDto gameDto = UserGameResponseDto.builder()
                .userGameId(1L)
                .appId(1091500L)
                .name("Cyberpunk 2077")
                .playtimeForever(7200)
                .build();

        given(steamSyncService.getUserGames(eq(testUser))).willReturn(List.of(gameDto));

        // when & then
        mockMvc.perform(get("/api/v1/steam/user-games")
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Cyberpunk 2077"))
                .andExpect(jsonPath("$.data[0].playtimeForever").value(7200));
    }
}
