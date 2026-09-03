package com.steamfolder.steamfolder.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.steamfolder.steamfolder.common.exception.GlobalExceptionHandler;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.common.security.config.SecurityConfig;
import com.steamfolder.steamfolder.common.security.jwt.JwtAuthenticationEntryPoint;
import com.steamfolder.steamfolder.common.security.jwt.JwtTokenProvider;
import com.steamfolder.steamfolder.domain.user.dto.request.UpdateProfileRequest;
import com.steamfolder.steamfolder.domain.user.dto.request.UpdateVisibilityRequest;
import com.steamfolder.steamfolder.domain.user.dto.response.UserResponse;
import com.steamfolder.steamfolder.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private com.steamfolder.steamfolder.domain.game.service.SteamSyncService steamSyncService;

    @MockitoBean
    private com.steamfolder.steamfolder.domain.user.repository.UserRepository userRepository;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UserPrincipal testPrincipal;
    private UsernamePasswordAuthenticationToken testAuth;
    private com.steamfolder.steamfolder.domain.user.entity.User testUser;

    @BeforeEach
    void setUp() {
        testPrincipal = UserPrincipal.builder()
                .userId(1L)
                .steamId("76561198000000001")
                .username("TestUser")
                .build();
        testAuth = new UsernamePasswordAuthenticationToken(testPrincipal, null, testPrincipal.getAuthorities());

        testUser = com.steamfolder.steamfolder.domain.user.entity.User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("TestUser")
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/users/me - 내 프로필 조회 200 OK")
    void getMyProfile_Returns200() throws Exception {
        // given
        UserResponse response = UserResponse.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("TestUser")
                .avatarUrl("https://avatars.steamstatic.com/test.jpg")
                .themeColor("#1A1B26")
                .isPublic(true)
                .build();

        given(userService.getMyProfile(1L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/users/me")
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.username").value("TestUser"))
                .andExpect(jsonPath("$.data.isPublic").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/profile - 쇼케이스 테마 색상 수정 200 OK")
    void updateProfile_Returns200() throws Exception {
        // given
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .themeColor("#2A2D3E")
                .build();

        UserResponse response = UserResponse.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("TestUser")
                .avatarUrl("https://avatars.steamstatic.com/test.jpg")
                .themeColor("#2A2D3E")
                .isPublic(true)
                .build();

        given(userService.updateProfile(eq(1L), any(UpdateProfileRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.themeColor").value("#2A2D3E"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/visibility - 쇼케이스 공개 범위 수정 200 OK")
    void updateVisibility_Returns200() throws Exception {
        // given
        UpdateVisibilityRequest request = UpdateVisibilityRequest.builder()
                .isPublic(false)
                .build();

        UserResponse response = UserResponse.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("TestUser")
                .isPublic(false)
                .build();

        given(userService.updateVisibility(eq(1L), any(UpdateVisibilityRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/users/me/visibility")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isPublic").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me - 회원 탈퇴 200 OK")
    void deleteAccount_Returns200() throws Exception {
        // given
        doNothing().when(userService).deleteAccount(1L);

        // when & then
        mockMvc.perform(delete("/api/v1/users/me")
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원 탈퇴 및 연동 해제가 성공적으로 완료되었습니다."));
    }

    @Test
    @DisplayName("GET /api/v1/users/me/games - 내 게임 라이브러리 커서 페이징 조회 200 OK")
    void getMyGames_Returns200() throws Exception {
        // given
        com.steamfolder.steamfolder.domain.game.dto.response.UserGameResponseDto item =
                com.steamfolder.steamfolder.domain.game.dto.response.UserGameResponseDto.builder()
                        .appId(730L)
                        .name("Counter-Strike 2")
                        .playtimeForever(5000)
                        .build();

        com.steamfolder.steamfolder.common.dto.CursorPageResponseDto<com.steamfolder.steamfolder.domain.game.dto.response.UserGameResponseDto> pageResponse =
                com.steamfolder.steamfolder.common.dto.CursorPageResponseDto.of(
                        java.util.List.of(item),
                        null,
                        false,
                        1L,
                        20
                );

        given(userRepository.findById(1L)).willReturn(java.util.Optional.of(testUser));
        given(steamSyncService.getUserGamesWithCursor(eq(testUser), any(), eq(20)))
                .willReturn(pageResponse);

        // when & then
        mockMvc.perform(get("/api/v1/users/me/games")
                        .with(authentication(testAuth))
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].appId").value(730L))
                .andExpect(jsonPath("$.data.content[0].name").value("Counter-Strike 2"))
                .andExpect(jsonPath("$.data.totalElements").value(1L));
    }

    @Test
    @DisplayName("GET /api/v1/users/me/games/filter - 내 보유 게임 동적 필터링 조회 200 OK")
    void filterMyGames_Returns200() throws Exception {
        // given
        com.steamfolder.steamfolder.domain.game.dto.response.UserGameResponseDto item =
                com.steamfolder.steamfolder.domain.game.dto.response.UserGameResponseDto.builder()
                        .appId(1091500L)
                        .name("Cyberpunk 2077")
                        .playtimeForever(7200)
                        .genres(List.of("Action", "RPG"))
                        .build();

        com.steamfolder.steamfolder.domain.game.dto.response.FilteredGamesResponseDto response =
                com.steamfolder.steamfolder.domain.game.dto.response.FilteredGamesResponseDto.of(List.of(item));

        given(userRepository.findById(1L)).willReturn(java.util.Optional.of(testUser));
        given(steamSyncService.filterUserGames(eq(testUser), any(), eq("playtime")))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/users/me/games/filter")
                        .with(authentication(testAuth))
                        .param("genres", "Action,RPG")
                        .param("sort", "playtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.games[0].appId").value(1091500L))
                .andExpect(jsonPath("$.data.games[0].name").value("Cyberpunk 2077"));
    }

    @Test
    @DisplayName("POST /api/v1/users/me/games/{appId}/achievements/sync - 특정 게임 도전과제 온디맨드 동기화 200 OK")
    void syncAchievements_Returns200() throws Exception {
        // given
        com.steamfolder.steamfolder.domain.game.dto.response.GameAchievementDetailResponseDto response =
                com.steamfolder.steamfolder.domain.game.dto.response.GameAchievementDetailResponseDto.builder()
                        .appId(1091500L)
                        .gameName("Cyberpunk 2077")
                        .totalAchievements(50)
                        .achievedAchievements(25)
                        .achievementRate(50.0)
                        .achievements(List.of())
                        .build();

        given(userRepository.findById(1L)).willReturn(java.util.Optional.of(testUser));
        given(steamSyncService.syncUserGameAchievements(eq(testUser), eq(1091500L)))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/users/me/games/1091500/achievements/sync")
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.appId").value(1091500L))
                .andExpect(jsonPath("$.data.totalAchievements").value(50))
                .andExpect(jsonPath("$.data.achievedAchievements").value(25))
                .andExpect(jsonPath("$.data.achievementRate").value(50.0));
    }

    @Test
    @DisplayName("GET /api/v1/users/me/games/{appId}/achievements - 특정 게임 도전과제 달성 상세 조회 200 OK")
    void getAchievements_Returns200() throws Exception {
        // given
        com.steamfolder.steamfolder.domain.game.dto.response.GameAchievementDetailResponseDto response =
                com.steamfolder.steamfolder.domain.game.dto.response.GameAchievementDetailResponseDto.builder()
                        .appId(1091500L)
                        .gameName("Cyberpunk 2077")
                        .totalAchievements(50)
                        .achievedAchievements(40)
                        .achievementRate(80.0)
                        .achievements(List.of())
                        .build();

        given(userRepository.findById(1L)).willReturn(java.util.Optional.of(testUser));
        given(steamSyncService.getGameAchievements(eq(testUser), eq(1091500L)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/users/me/games/1091500/achievements")
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.appId").value(1091500L))
                .andExpect(jsonPath("$.data.achievementRate").value(80.0));
    }
}
