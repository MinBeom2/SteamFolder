package com.steamfolder.steamfolder.domain.game.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.game.client.SteamApiClient;
import com.steamfolder.steamfolder.domain.game.dto.response.CooldownResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.SyncResultResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.UserGameResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamOwnedGamesResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamPlayerSummaryResponseDto;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import com.steamfolder.steamfolder.domain.game.repository.GameRepository;
import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SteamSyncServiceTest {

    @Mock
    private SteamApiClient steamApiClient;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private UserGameRepository userGameRepository;

    @Mock
    private SteamSyncCooldownManager cooldownManager;

    @InjectMocks
    private SteamSyncService steamSyncService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("SyncTester")
                .avatarUrl("https://example.com/avatar.jpg")
                .isPublic(true)
                .build();
    }

    @Nested
    @DisplayName("스팀 보유 게임 실시간 동기화 (syncUserGames)")
    class SyncUserGamesTest {

        @Test
        @DisplayName("성공: 신규 게임 및 보유 정보가 정상적으로 저장/동기화된다.")
        void syncUserGames_Success() {
            // given
            SteamPlayerSummaryResponseDto.PlayerDto publicPlayer = new SteamPlayerSummaryResponseDto.PlayerDto();
            ReflectionTestUtils.setField(publicPlayer, "steamId", testUser.getSteamId());
            ReflectionTestUtils.setField(publicPlayer, "communityVisibilityState", 3); // Public
            given(steamApiClient.getPlayerSummary(testUser.getSteamId())).willReturn(Optional.of(publicPlayer));

            SteamOwnedGamesResponseDto.SteamGameDto game1 = new SteamOwnedGamesResponseDto.SteamGameDto();
            ReflectionTestUtils.setField(game1, "appId", 1091500L);
            ReflectionTestUtils.setField(game1, "name", "Cyberpunk 2077");
            ReflectionTestUtils.setField(game1, "playtimeForever", 7200);
            ReflectionTestUtils.setField(game1, "rtimeLastPlayed", 1698240000L);

            SteamOwnedGamesResponseDto.ResponseData ownedGamesData = new SteamOwnedGamesResponseDto.ResponseData();
            ReflectionTestUtils.setField(ownedGamesData, "gameCount", 1);
            ReflectionTestUtils.setField(ownedGamesData, "games", List.of(game1));
            given(steamApiClient.getUserOwnedGames(testUser.getSteamId())).willReturn(ownedGamesData);

            given(gameRepository.findById(1091500L)).willReturn(Optional.empty());
            Game savedGame = Game.builder()
                    .appId(1091500L)
                    .name("Cyberpunk 2077")
                    .build();
            given(gameRepository.save(any(Game.class))).willReturn(savedGame);

            given(userGameRepository.findByUserAndGame(eq(testUser), any(Game.class))).willReturn(Optional.empty());

            // when
            SyncResultResponseDto result = steamSyncService.syncUserGames(testUser);

            // then
            assertThat(result.getSyncedGameCount()).isEqualTo(1);
            assertThat(result.getNewlyAddedCount()).isEqualTo(1);
            assertThat(result.getLastSyncedAt()).isNotNull();

            verify(cooldownManager).validateCooldown(testUser.getSteamId());
            verify(cooldownManager).applyCooldown(testUser.getSteamId());
            verify(userGameRepository).save(any(UserGame.class));
        }

        @Test
        @DisplayName("실패: 스팀 프로필이 비공개(communityvisibilitystate != 3)인 경우 예외 발생")
        void syncUserGames_PrivateProfile_ThrowsException() {
            // given
            SteamPlayerSummaryResponseDto.PlayerDto privatePlayer = new SteamPlayerSummaryResponseDto.PlayerDto();
            ReflectionTestUtils.setField(privatePlayer, "steamId", testUser.getSteamId());
            ReflectionTestUtils.setField(privatePlayer, "communityVisibilityState", 1); // Private
            given(steamApiClient.getPlayerSummary(testUser.getSteamId())).willReturn(Optional.of(privatePlayer));

            // when & then
            assertThatThrownBy(() -> steamSyncService.syncUserGames(testUser))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STEAM_PROFILE_PRIVATE);

            verify(steamApiClient, never()).getUserOwnedGames(anyString());
        }

        @Test
        @DisplayName("실패: 쿨다운 제한 중인 경우 예외 발생")
        void syncUserGames_CooldownActive_ThrowsException() {
            // given
            doThrow(new CustomException(ErrorCode.SYNC_COOLDOWN_ACTIVE))
                    .when(cooldownManager).validateCooldown(testUser.getSteamId());

            // when & then
            assertThatThrownBy(() -> steamSyncService.syncUserGames(testUser))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SYNC_COOLDOWN_ACTIVE);
        }
    }

    @Nested
    @DisplayName("동기화 쿨다운 상태 조회 (getCooldownStatus)")
    class GetCooldownStatusTest {

        @Test
        @DisplayName("성공: 쿨다운 잔여 시간 및 최근 동기화 일시를 정상 반환한다.")
        void getCooldownStatus_Success() {
            // given
            given(cooldownManager.getRemainingSeconds(testUser.getSteamId())).willReturn(120L);
            LocalDateTime now = LocalDateTime.now();
            given(cooldownManager.getLastSyncedAt(testUser.getSteamId())).willReturn(now);

            // when
            CooldownResponseDto status = steamSyncService.getCooldownStatus(testUser);

            // then
            assertThat(status.isCooldownActive()).isTrue();
            assertThat(status.getRemainingSeconds()).isEqualTo(120L);
            assertThat(status.getLastSyncedAt()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("로컬 DB 동기화 게임 목록 조회 (getUserGames)")
    class GetUserGamesTest {

        @Test
        @DisplayName("성공: 유저가 보유한 게임 목록을 플레이타임 내림차순으로 반환한다.")
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
            List<UserGameResponseDto> games = steamSyncService.getUserGames(testUser);

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

            // size=2 요청 시 3개(size+1) 반환
            given(userGameRepository.findByUserOrderByIdDesc(eq(testUser), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(List.of(ug1, ug2, ug3));
            given(userGameRepository.countByUser(testUser)).willReturn(10L);

            // when
            com.steamfolder.steamfolder.common.dto.CursorPageResponseDto<UserGameResponseDto> response =
                    steamSyncService.getUserGamesWithCursor(testUser, null, 2);

            // then
            assertThat(response.getContent()).hasSize(2);
            assertThat(response.isHasNext()).isTrue();
            assertThat(response.getNextCursor()).isEqualTo(2L);
            assertThat(response.getTotalElements()).isEqualTo(10L);
            assertThat(response.getPageSize()).isEqualTo(2);
        }
    }
}
