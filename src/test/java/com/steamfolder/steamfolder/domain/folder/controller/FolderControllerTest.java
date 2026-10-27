package com.steamfolder.steamfolder.domain.folder.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.steamfolder.steamfolder.common.exception.GlobalExceptionHandler;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.common.security.config.SecurityConfig;
import com.steamfolder.steamfolder.common.security.jwt.JwtAuthenticationEntryPoint;
import com.steamfolder.steamfolder.common.security.jwt.JwtTokenProvider;
import com.steamfolder.steamfolder.domain.folder.dto.request.CreateFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.CopyFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.MoveFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.UpdateFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.UpdatePositionsRequest;
import com.steamfolder.steamfolder.domain.folder.dto.response.FolderResponse;
import com.steamfolder.steamfolder.domain.folder.service.FolderService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FolderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class FolderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FolderService folderService;

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
    @DisplayName("POST /api/v1/folders - 폴더 생성 201 Created")
    void createFolder_Returns201() throws Exception {
        // given
        CreateFolderRequest request = CreateFolderRequest.builder()
                .name("인디 명작")
                .themeColor("#00F0FF")
                .icon("gamepad")
                .build();

        FolderResponse response = FolderResponse.builder()
                .id(1L)
                .name("인디 명작")
                .themeColor("#00F0FF")
                .icon("gamepad")
                .positionX(0)
                .positionY(0)
                .depth(0)
                .build();

        given(folderService.createFolder(eq(1L), any(CreateFolderRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/folders")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.name").value("인디 명작"));
    }

    @Test
    @DisplayName("GET /api/v1/folders - 내 전체 폴더 계층 목록 조회 200 OK")
    void getMyFolders_Returns200() throws Exception {
        // given
        FolderResponse response = FolderResponse.builder()
                .id(1L)
                .name("루트 폴더")
                .depth(0)
                .build();

        given(folderService.getMyFolderHierarchy(1L)).willReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/v1/folders")
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].name").value("루트 폴더"));
    }

    @Test
    @DisplayName("GET /api/v1/folders/{folderId} - 특정 폴더 상세 조회 200 OK")
    void getFolderDetail_Returns200() throws Exception {
        // given
        FolderResponse response = FolderResponse.builder()
                .id(10L)
                .name("특정 폴더")
                .depth(1)
                .build();

        given(folderService.getFolderDetail(eq(1L), eq(10L))).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/folders/10")
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10L))
                .andExpect(jsonPath("$.data.name").value("특정 폴더"));
    }

    @Test
    @DisplayName("GET /api/v1/folders/{folderId} - 비로그인 게스트의 공개 폴더 조회 200 OK")
    void getFolderDetail_Guest_Returns200() throws Exception {
        // given
        FolderResponse response = FolderResponse.builder()
                .id(20L)
                .name("공개 게스트 폴더")
                .depth(1)
                .build();

        given(folderService.getFolderDetail(eq(null), eq(20L))).willReturn(response);

        // when & then (인증 없이 요청)
        mockMvc.perform(get("/api/v1/folders/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(20L))
                .andExpect(jsonPath("$.data.name").value("공개 게스트 폴더"));
    }

    @Test
    @DisplayName("PATCH /api/v1/folders/{folderId} - 폴더 정보 수정 200 OK")
    void updateFolder_Returns200() throws Exception {
        // given
        UpdateFolderRequest request = UpdateFolderRequest.builder()
                .name("수정된 이름")
                .themeColor("#EF4444")
                .build();

        FolderResponse response = FolderResponse.builder()
                .id(1L)
                .name("수정된 이름")
                .themeColor("#EF4444")
                .build();

        given(folderService.updateFolder(eq(1L), eq(1L), any(UpdateFolderRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/folders/1")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("수정된 이름"));
    }

    @Test
    @DisplayName("PATCH /api/v1/folders/{folderId}/move - 폴더 이동 200 OK")
    void moveFolder_Returns200() throws Exception {
        // given
        MoveFolderRequest request = MoveFolderRequest.builder()
                .targetParentFolderId(2L)
                .targetPositionX(1)
                .targetPositionY(0)
                .build();

        FolderResponse response = FolderResponse.builder()
                .id(1L)
                .name("이동된 폴더")
                .parentFolderId(2L)
                .positionX(1)
                .positionY(0)
                .depth(1)
                .build();

        given(folderService.moveFolder(eq(1L), eq(1L), any(MoveFolderRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/folders/1/move")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.parentFolderId").value(2L))
                .andExpect(jsonPath("$.data.depth").value(1));
    }

    @Test
    @DisplayName("PATCH /api/v1/folders/positions - 2D 좌표 일괄 업데이트 200 OK")
    void updatePositions_Returns200() throws Exception {
        // given
        UpdatePositionsRequest request = UpdatePositionsRequest.builder()
                .positions(List.of(
                        UpdatePositionsRequest.ItemPositionDto.builder()
                                .type("FOLDER")
                                .id(1L)
                                .positionX(2)
                                .positionY(3)
                                .build()
                ))
                .build();

        // when & then
        mockMvc.perform(patch("/api/v1/folders/positions")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/folders/{folderId} - 폴더 삭제 200 OK")
    void deleteFolder_Returns200() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/folders/1")
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void copyFolder_Returns201WithCopiedContents() throws Exception {
        FolderResponse response = FolderResponse.builder().id(100L).name("Copy")
                .parentFolderId(2L).gameCount(1).childFolderCount(1)
                .childFolders(List.of(FolderResponse.builder().id(101L).name("Child").build()))
                .games(List.of(FolderResponse.FolderGameDto.builder().id(200L).appId(10L).build()))
                .build();
        given(folderService.copyFolder(eq(1L), eq(10L), any(CopyFolderRequest.class))).willReturn(response);
        mockMvc.perform(post("/api/v1/folders/10/copy")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CopyFolderRequest.builder()
                                .targetParentFolderId(2L).targetPositionX(1).targetPositionY(0).build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.games[0].appId").value(10))
                .andExpect(jsonPath("$.data.childFolders[0].id").value(101));
    }

    @Test
    void copyFolder_RejectsNegativeCoordinatesAndOverlongNames() throws Exception {
        mockMvc.perform(post("/api/v1/folders/10/copy")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CopyFolderRequest.builder().targetPositionX(-1).build())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/folders/10/copy")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CopyFolderRequest.builder()
                                .name("x".repeat(101)).build())))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(folderService, org.mockito.Mockito.never())
                .copyFolder(any(), any(), any());
    }
}
