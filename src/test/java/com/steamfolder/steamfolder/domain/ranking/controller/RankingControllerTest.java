package com.steamfolder.steamfolder.domain.ranking.controller;

import com.steamfolder.steamfolder.common.exception.GlobalExceptionHandler;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.common.security.config.SecurityConfig;
import com.steamfolder.steamfolder.common.security.jwt.JwtAuthenticationEntryPoint;
import com.steamfolder.steamfolder.common.security.jwt.JwtTokenProvider;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseRankingItemDto;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseRankingResponse;
import com.steamfolder.steamfolder.domain.showcase.service.ShowcaseService;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RankingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShowcaseService showcaseService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Test
    @DisplayName("GET /api/v1/rankings/showcases: 비로그인 게스트도 첫 페이지 랭킹을 정상 조회할 수 있다 (myRanking == null).")
    void getShowcaseRankings_firstPage_success() throws Exception {
        // given
        ShowcaseRankingItemDto item1 = ShowcaseRankingItemDto.builder()
                .rank(1L)
                .userId(5L)
                .username("GamerGod")
                .avatarUrl("https://avatars.steamstatic.com/avatar1.jpg")
                .likeCount(120)
                .totalGamesCount(350L)
                .build();

        ShowcaseRankingItemDto item2 = ShowcaseRankingItemDto.builder()
                .rank(2L)
                .userId(12L)
                .username("IndieLover")
                .avatarUrl("https://avatars.steamstatic.com/avatar2.jpg")
                .likeCount(85)
                .totalGamesCount(140L)
                .build();

        ShowcaseRankingResponse response = ShowcaseRankingResponse.builder()
                .content(List.of(item1, item2))
                .myRanking(null)
                .nextCursorLikeCount(85)
                .nextCursorUserId(12L)
                .hasNext(true)
                .pageSize(10)
                .build();

        given(showcaseService.getShowcaseRankings(null, null, 10, null)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/rankings/showcases")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("인기 쇼케이스 랭킹 조회 성공"))
                .andExpect(jsonPath("$.data.content[0].rank").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value(5))
                .andExpect(jsonPath("$.data.content[0].username").value("GamerGod"))
                .andExpect(jsonPath("$.data.content[0].likeCount").value(120))
                .andExpect(jsonPath("$.data.content[0].totalGamesCount").value(350))
                .andExpect(jsonPath("$.data.content[1].rank").value(2))
                .andExpect(jsonPath("$.data.myRanking").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursorLikeCount").value(85))
                .andExpect(jsonPath("$.data.nextCursorUserId").value(12))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.pageSize").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/rankings/showcases: 복합 커서 파라미터가 정상 전달되어 다음 페이지를 조회한다.")
    void getShowcaseRankings_withCursor_success() throws Exception {
        // given
        ShowcaseRankingItemDto item3 = ShowcaseRankingItemDto.builder()
                .rank(3L)
                .userId(20L)
                .username("RetroFan")
                .avatarUrl("https://avatars.steamstatic.com/avatar3.jpg")
                .likeCount(50)
                .totalGamesCount(60L)
                .build();

        ShowcaseRankingResponse response = ShowcaseRankingResponse.builder()
                .content(List.of(item3))
                .myRanking(null)
                .nextCursorLikeCount(null)
                .nextCursorUserId(null)
                .hasNext(false)
                .pageSize(5)
                .build();

        given(showcaseService.getShowcaseRankings(85, 12L, 5, null)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/rankings/showcases")
                        .param("cursorLikeCount", "85")
                        .param("cursorUserId", "12")
                        .param("limit", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].rank").value(3))
                .andExpect(jsonPath("$.data.content[0].userId").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/rankings/showcases: 로그인 유저는 순위 밖이어도 myRanking에 본인의 순위와 좋아요 수가 함께 반환된다.")
    void getShowcaseRankings_authenticated_returnsMyRanking() throws Exception {
        // given
        UserPrincipal principal = UserPrincipal.builder()
                .userId(99L)
                .steamId("76561198000000099")
                .username("MyShowcase")
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        ShowcaseRankingItemDto myRanking = ShowcaseRankingItemDto.builder()
                .rank(42L)
                .userId(99L)
                .username("MyShowcase")
                .avatarUrl("https://my-avatar.jpg")
                .likeCount(15)
                .totalGamesCount(12L)
                .build();

        ShowcaseRankingResponse response = ShowcaseRankingResponse.builder()
                .content(List.of())
                .myRanking(myRanking)
                .nextCursorLikeCount(null)
                .nextCursorUserId(null)
                .hasNext(false)
                .pageSize(10)
                .build();

        given(showcaseService.getShowcaseRankings(null, null, 10, 99L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/rankings/showcases")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.myRanking.rank").value(42))
                .andExpect(jsonPath("$.data.myRanking.userId").value(99))
                .andExpect(jsonPath("$.data.myRanking.username").value("MyShowcase"))
                .andExpect(jsonPath("$.data.myRanking.likeCount").value(15))
                .andExpect(jsonPath("$.data.myRanking.totalGamesCount").value(12));
    }
}
