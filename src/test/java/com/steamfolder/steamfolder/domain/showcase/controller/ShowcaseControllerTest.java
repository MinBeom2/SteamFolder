package com.steamfolder.steamfolder.domain.showcase.controller;

import com.steamfolder.steamfolder.common.exception.GlobalExceptionHandler;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.common.security.config.SecurityConfig;
import com.steamfolder.steamfolder.common.security.jwt.JwtAuthenticationEntryPoint;
import com.steamfolder.steamfolder.common.security.jwt.JwtTokenProvider;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseResponse;
import com.steamfolder.steamfolder.domain.showcase.service.ShowcaseService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShowcaseController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ShowcaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShowcaseService showcaseService;

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
                .username("ShowcaseKing")
                .build();
        testAuth = new UsernamePasswordAuthenticationToken(testPrincipal, null, testPrincipal.getAuthorities());
    }

    @Test
    @DisplayName("GET /api/v1/showcases/me - 내 쇼케이스 뷰 조회 200 OK")
    void getMyShowcase_Returns200() throws Exception {
        // given
        ShowcaseResponse response = ShowcaseResponse.builder()
                .user(ShowcaseResponse.ShowcaseUserDto.builder()
                        .id(1L)
                        .steamId("76561198000000001")
                        .username("ShowcaseKing")
                        .isPublic(true)
                        .build())
                .stats(ShowcaseResponse.ShowcaseStatsDto.builder()
                        .totalFolders(3)
                        .totalOwnedGames(20)
                        .build())
                .rootFolders(List.of())
                .build();

        given(showcaseService.getMyShowcase(1L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/showcases/me")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.username").value("ShowcaseKing"))
                .andExpect(jsonPath("$.data.stats.totalFolders").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/showcases/{identifier} - 게스트 쇼케이스 공개 조회 200 OK")
    void getPublicShowcase_Returns200() throws Exception {
        // given
        ShowcaseResponse response = ShowcaseResponse.builder()
                .user(ShowcaseResponse.ShowcaseUserDto.builder()
                        .id(1L)
                        .steamId("76561198000000001")
                        .username("ShowcaseKing")
                        .isPublic(true)
                        .build())
                .stats(ShowcaseResponse.ShowcaseStatsDto.builder()
                        .totalFolders(3)
                        .build())
                .rootFolders(List.of())
                .build();

        given(showcaseService.getPublicShowcase(eq("76561198000000001"), any())).willReturn(response);

        // when & then (비로그인 요청)
        mockMvc.perform(get("/api/v1/showcases/76561198000000001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.username").value("ShowcaseKing"));
    }
}
