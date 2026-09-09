package com.steamfolder.steamfolder.domain.game.service;

import com.steamfolder.steamfolder.common.dto.CursorPageResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.FilteredGamesResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.UserGameResponseDto;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserGameQueryServiceTest {

    @Mock
    private UserGameRepository userGameRepository;

    @InjectMocks
    private UserGameQueryService userGameQueryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("testUser")
                .build();
    }

    @Nested
    @DisplayName("유저 보유 게임 전체 목록 조회 (getUserGames)")
    class GetUserGamesTest {

        @Test
        @DisplayName("성공: 플레이타임 내림차순으로 전체 게임 목록을 반환한다.")
        void getUserGames_Success() {
            // given
            Game game = Game.builder()
                    .appId(1091500L)
                    .name("Cyberpunk 2077")
                    .genres(List.of("RPG", "Action"))
                    .developer("CD Projekt Red")
                    .build();

            UserGame userGame = UserGame.builder()
                    .id(1L)
                    .user(testUser)
                    .game(game)
                    .playtimeForever(7200)
                    .build();

            given(userGameRepository.findByUserOrderByPlaytimeForeverDesc(testUser))
                    .willReturn(List.of(userGame));

            // when
            List<UserGameResponseDto> games = userGameQueryService.getUserGames(testUser);

            // then
            assertThat(games).hasSize(1);
            assertThat(games.get(0).getName()).isEqualTo("Cyberpunk 2077");
            assertThat(games.get(0).getPlaytimeForever()).isEqualTo(7200);
        }
    }

    @Nested
    @DisplayName("No-Offset 커서 기반 유저 게임 페이징 조회 (getUserGamesWithCursor)")
    class GetUserGamesWithCursorTest {

        @Test
        @DisplayName("성공: 첫 페이지 조회 시 다음 커서 및 hasNext가 정상 계산된다.")
        void getUserGamesWithCursor_FirstPage_Success() {
            // given
            Game game1 = Game.builder().appId(10L).name("Game 1").genres(List.of("Action")).build();
            Game game2 = Game.builder().appId(20L).name("Game 2").genres(List.of("RPG")).build();
            Game game3 = Game.builder().appId(30L).name("Game 3").genres(List.of("Strategy")).build();

            UserGame ug1 = UserGame.builder().id(3L).user(testUser).game(game1).playtimeForever(100).build();
            UserGame ug2 = UserGame.builder().id(2L).user(testUser).game(game2).playtimeForever(50).build();
            UserGame ug3 = UserGame.builder().id(1L).user(testUser).game(game3).playtimeForever(10).build();

            given(userGameRepository.findByUserOrderByIdDesc(eq(testUser), any(Pageable.class)))
                    .willReturn(List.of(ug1, ug2, ug3));
            given(userGameRepository.countByUser(testUser)).willReturn(10L);

            // when
            CursorPageResponseDto<UserGameResponseDto> response =
                    userGameQueryService.getUserGamesWithCursor(testUser, null, 2);

            // then
            assertThat(response.getContent()).hasSize(2);
            assertThat(response.isHasNext()).isTrue();
            assertThat(response.getNextCursor()).isEqualTo(2L);
            assertThat(response.getTotalElements()).isEqualTo(10L);
            assertThat(response.getPageSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("성공: 커서가 주어졌을 때 findByUserAndIdLessThanOrderByIdDesc 호출 및 마지막 페이지 검증")
        void getUserGamesWithCursor_NextPage_LastPage() {
            // given
            Game game = Game.builder().appId(10L).name("Last Game").genres(List.of("Indie")).build();
            UserGame ug = UserGame.builder().id(1L).user(testUser).game(game).playtimeForever(5).build();

            given(userGameRepository.findByUserAndIdLessThanOrderByIdDesc(eq(testUser), eq(2L), any(Pageable.class)))
                    .willReturn(List.of(ug));
            given(userGameRepository.countByUser(testUser)).willReturn(10L);

            // when
            CursorPageResponseDto<UserGameResponseDto> response =
                    userGameQueryService.getUserGamesWithCursor(testUser, 2L, 2);

            // then
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.isHasNext()).isFalse();
            assertThat(response.getNextCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("보유 게임 다중 조건 동적 필터링 및 DB 정렬 (filterUserGames)")
    class FilterUserGamesTest {

        private UserGame ug1;
        private UserGame ug2;
        private UserGame ug3;

        @BeforeEach
        void setUpFilterGames() {
            Game game1 = Game.builder().appId(10L).name("Cyberpunk 2077").genres(List.of("Action", "RPG")).build();
            Game game2 = Game.builder().appId(20L).name("Stardew Valley").genres(List.of("Simulation", "RPG")).build();
            Game game3 = Game.builder().appId(30L).name("Counter-Strike 2").genres(List.of("Action", "FPS")).build();

            ug1 = UserGame.builder()
                    .id(1L)
                    .user(testUser)
                    .game(game1)
                    .playtimeForever(7200)
                    .lastPlayedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                    .build();

            ug2 = UserGame.builder()
                    .id(2L)
                    .user(testUser)
                    .game(game2)
                    .playtimeForever(3000)
                    .lastPlayedAt(LocalDateTime.of(2026, 8, 15, 12, 0))
                    .build();

            ug3 = UserGame.builder()
                    .id(3L)
                    .user(testUser)
                    .game(game3)
                    .playtimeForever(600)
                    .lastPlayedAt(LocalDateTime.of(2026, 9, 5, 20, 0))
                    .build();
        }

        @Test
        @DisplayName("성공: 장르 조건(Action)으로 필터링 시 일치하는 게임만 반환")
        void filterUserGames_ByGenre_Success() {
            // given
            given(userGameRepository.findAll(ArgumentMatchers.<Specification<UserGame>>any(), any(Sort.class)))
                    .willReturn(List.of(ug1, ug3));

            // when
            FilteredGamesResponseDto response = userGameQueryService.filterUserGames(testUser, List.of("Action"), "playtime");

            // then
            assertThat(response.getTotalCount()).isEqualTo(2);
            assertThat(response.getGames()).extracting("name").containsExactly("Cyberpunk 2077", "Counter-Strike 2");
            // 호환 필드 검증
            assertThat(response.getContent()).hasSize(2);
            assertThat(response.isHasNext()).isFalse();
            assertThat(response.getTotalElements()).isEqualTo(2L);
        }

        @Test
        @DisplayName("성공: 최근 플레이순 정렬(sort=recent) Sort 객체 전달 검증")
        void filterUserGames_SortByRecent_Success() {
            // given
            ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
            given(userGameRepository.findAll(ArgumentMatchers.<Specification<UserGame>>any(), sortCaptor.capture()))
                    .willReturn(List.of(ug3, ug1, ug2));

            // when
            FilteredGamesResponseDto response = userGameQueryService.filterUserGames(testUser, null, "recent");

            // then
            assertThat(response.getGames()).extracting("name")
                    .containsExactly("Counter-Strike 2", "Cyberpunk 2077", "Stardew Valley");

            Sort capturedSort = sortCaptor.getValue();
            assertThat(capturedSort.getOrderFor("lastPlayedAt")).isNotNull();
            assertThat(capturedSort.getOrderFor("lastPlayedAt").isDescending()).isTrue();
        }

        @Test
        @DisplayName("성공: 이름 가나다 오름차순 정렬(sort=name-asc) Sort 객체 전달 검증")
        void filterUserGames_SortByNameAsc_Success() {
            // given
            ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
            given(userGameRepository.findAll(ArgumentMatchers.<Specification<UserGame>>any(), sortCaptor.capture()))
                    .willReturn(List.of(ug3, ug1, ug2));

            // when
            FilteredGamesResponseDto response = userGameQueryService.filterUserGames(testUser, null, "name-asc");

            // then
            assertThat(response.getGames()).extracting("name")
                    .containsExactly("Counter-Strike 2", "Cyberpunk 2077", "Stardew Valley");

            Sort capturedSort = sortCaptor.getValue();
            assertThat(capturedSort.getOrderFor("game.name")).isNotNull();
            assertThat(capturedSort.getOrderFor("game.name").isAscending()).isTrue();
        }

        @Test
        @DisplayName("성공: 이름 내림차순 정렬(sort=name-desc) Sort 객체 전달 검증")
        void filterUserGames_SortByNameDesc_Success() {
            // given
            ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
            given(userGameRepository.findAll(ArgumentMatchers.<Specification<UserGame>>any(), sortCaptor.capture()))
                    .willReturn(List.of(ug2, ug1, ug3));

            // when
            FilteredGamesResponseDto response = userGameQueryService.filterUserGames(testUser, null, "name-desc");

            // then
            assertThat(response.getGames()).extracting("name")
                    .containsExactly("Stardew Valley", "Cyberpunk 2077", "Counter-Strike 2");

            Sort capturedSort = sortCaptor.getValue();
            assertThat(capturedSort.getOrderFor("game.name")).isNotNull();
            assertThat(capturedSort.getOrderFor("game.name").isDescending()).isTrue();
        }

        @Test
        @DisplayName("성공: 플레이타임 기본값 정렬(sort=playtime) 검증")
        void filterUserGames_SortByPlaytimeDefault_Success() {
            // given
            ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
            given(userGameRepository.findAll(ArgumentMatchers.<Specification<UserGame>>any(), sortCaptor.capture()))
                    .willReturn(List.of(ug1, ug2, ug3));

            // when
            FilteredGamesResponseDto response = userGameQueryService.filterUserGames(testUser, null, "playtime");

            // then
            assertThat(response.getGames()).extracting("name")
                    .containsExactly("Cyberpunk 2077", "Stardew Valley", "Counter-Strike 2");

            Sort capturedSort = sortCaptor.getValue();
            assertThat(capturedSort.getOrderFor("playtimeForever")).isNotNull();
            assertThat(capturedSort.getOrderFor("playtimeForever").isDescending()).isTrue();
        }
    }
}
