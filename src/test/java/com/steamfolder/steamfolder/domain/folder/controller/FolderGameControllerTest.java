package com.steamfolder.steamfolder.domain.folder.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.steamfolder.steamfolder.common.exception.GlobalExceptionHandler;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.common.security.config.SecurityConfig;
import com.steamfolder.steamfolder.common.security.jwt.JwtAuthenticationEntryPoint;
import com.steamfolder.steamfolder.common.security.jwt.JwtTokenProvider;
import com.steamfolder.steamfolder.domain.folder.dto.request.AddFolderGameRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.MoveFolderGameRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.RemoveFolderGamesRequest;
import com.steamfolder.steamfolder.domain.folder.dto.response.FolderGameDetailResponse;
import com.steamfolder.steamfolder.domain.folder.service.FolderGameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FolderGameController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class FolderGameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FolderGameService folderGameService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UserPrincipal testPrincipal;
    private UsernamePasswordAuthenticationToken testAuth;

    @BeforeEach
    void setUp() {
        testPrincipal = UserPrincipal.builder()
                .userId(1L)
                .steamId("76561198000000001")
                .username("FolderMaster")
                .build();
        testAuth = new UsernamePasswordAuthenticationToken(testPrincipal, null, testPrincipal.getAuthorities());
    }

    @Test
    @DisplayName("POST /api/v1/folders/{folderId}/games - 폴더 내 게임 추가 201 Created")
    void addGameToFolder_Returns201() throws Exception {
        // given
        AddFolderGameRequest request = AddFolderGameRequest.builder()
                .appId(730L)
                .build();

        FolderGameDetailResponse response = FolderGameDetailResponse.builder()
                .id(100L)
                .folderId(10L)
                .appId(730L)
                .name("Counter-Strike 2")
                .positionX(0)
                .positionY(0)
                .createdAt(LocalDateTime.now())
                .build();

        given(folderGameService.addGameToFolder(eq(1L), eq(10L), any(AddFolderGameRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/folders/10/games")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.appId").value(730L))
                .andExpect(jsonPath("$.data.name").value("Counter-Strike 2"));
    }

    @Test
    @DisplayName("POST /api/v1/folders/{folderId}/games/batch - 게임 여러 개 일괄 추가 201 Created")
    void batchAddGamesToFolder_Returns201() throws Exception {
        // given
        com.steamfolder.steamfolder.domain.folder.dto.request.BatchAddFolderGamesRequest request =
                com.steamfolder.steamfolder.domain.folder.dto.request.BatchAddFolderGamesRequest.builder()
                        .appIds(List.of(730L, 570L))
                        .build();

        FolderGameDetailResponse item = FolderGameDetailResponse.builder()
                .id(100L)
                .folderId(10L)
                .appId(730L)
                .name("Counter-Strike 2")
                .positionX(0)
                .positionY(0)
                .createdAt(LocalDateTime.now())
                .build();

        given(folderGameService.batchAddGamesToFolder(eq(1L), eq(10L), any(com.steamfolder.steamfolder.domain.folder.dto.request.BatchAddFolderGamesRequest.class)))
                .willReturn(List.of(item));

        // when & then
        mockMvc.perform(post("/api/v1/folders/10/games/batch")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].appId").value(730L));
    }

    @Test
    @DisplayName("DELETE /api/v1/folders/{folderId}/games/{folderGameId} - 특정 게임 인스턴스 단일 삭제 200 OK")
    void removeFolderGame_Returns200() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/folders/10/games/100")
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/folders/{folderId}/games - 선택 게임 인스턴스들 일괄 삭제 200 OK")
    void removeFolderGames_Returns200() throws Exception {
        // given
        RemoveFolderGamesRequest request = RemoveFolderGamesRequest.builder()
                .folderGameIds(List.of(100L, 101L))
                .build();

        // when & then
        mockMvc.perform(delete("/api/v1/folders/10/games")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/folders/{folderId}/games/{folderGameId}/move - 게임 인스턴스 폴더 간 이동 200 OK")
    void moveGameToAnotherFolder_Returns200() throws Exception {
        // given
        MoveFolderGameRequest request = MoveFolderGameRequest.builder()
                .targetFolderId(20L)
                .targetPositionX(2)
                .targetPositionY(1)
                .build();

        FolderGameDetailResponse response = FolderGameDetailResponse.builder()
                .id(200L)
                .folderId(20L)
                .appId(730L)
                .name("Counter-Strike 2")
                .positionX(2)
                .positionY(1)
                .build();

        given(folderGameService.moveGameToAnotherFolder(eq(1L), eq(10L), eq(100L), any(MoveFolderGameRequest.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/folders/10/games/100/move")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.folderId").value(20L))
                .andExpect(jsonPath("$.data.positionX").value(2));
    }

    @Test
    @DisplayName("PATCH /api/v1/folders/{folderId}/games/move - 게임 여러 개 일괄 이동 200 OK")
    void batchMoveFolderGames_Returns200() throws Exception {
        // given
        com.steamfolder.steamfolder.domain.folder.dto.request.BatchMoveFolderGamesRequest request =
                com.steamfolder.steamfolder.domain.folder.dto.request.BatchMoveFolderGamesRequest.builder()
                        .folderGameIds(List.of(100L))
                        .targetFolderId(20L)
                        .build();

        FolderGameDetailResponse response = FolderGameDetailResponse.builder()
                .id(200L)
                .folderId(20L)
                .appId(730L)
                .name("Counter-Strike 2")
                .positionX(0)
                .positionY(1)
                .build();

        given(folderGameService.batchMoveFolderGames(eq(1L), eq(10L), any(com.steamfolder.steamfolder.domain.folder.dto.request.BatchMoveFolderGamesRequest.class)))
                .willReturn(List.of(response));

        // when & then
        mockMvc.perform(patch("/api/v1/folders/10/games/move")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].folderId").value(20L));
    }
}
