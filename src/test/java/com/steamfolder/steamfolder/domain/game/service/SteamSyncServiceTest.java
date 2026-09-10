package com.steamfolder.steamfolder.domain.game.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.game.client.SteamApiClient;
import com.steamfolder.steamfolder.domain.game.dto.response.CooldownResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.SyncResultResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamOwnedGamesResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamPlayerAchievementsResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamPlayerSummaryResponseDto;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import com.steamfolder.steamfolder.domain.game.repository.GameRepository;
import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import java.util.ArrayList;
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

    @Mock
    private GameMetadataEnrichmentService gameMetadataEnrichmentService;

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
            verify(gameMetadataEnrichmentService).enrichGameMetadataAsync(List.of(1091500L));
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
    @DisplayName("유저 게임 도전과제(업적) 실시간 동기화 (syncUserGameAchievements)")
    class SyncUserGameAchievementsTest {

        @Test
        @DisplayName("성공: 10개 업적 중 8개 달성 시 총 개수, 달성 개수, 달성률 80.0%가 정확히 계산되어 저장된다")
        void syncUserGameAchievements_Success() {
            // given
            Game game = Game.builder().appId(1091500L).name("Cyberpunk 2077").build();
            UserGame userGame = UserGame.builder().id(1L).user(testUser).game(game).build();

            given(userGameRepository.findByUserAndGame_AppId(testUser, 1091500L))
                    .willReturn(Optional.of(userGame));

            SteamPlayerAchievementsResponseDto.PlayerStats stats = new SteamPlayerAchievementsResponseDto.PlayerStats();
            List<SteamPlayerAchievementsResponseDto.AchievementDto> dtoList = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                var a = new SteamPlayerAchievementsResponseDto.AchievementDto();
                org.springframework.test.util.ReflectionTestUtils.setField(a, "apiName", "ACH_" + i);
                org.springframework.test.util.ReflectionTestUtils.setField(a, "achieved", 1);
                org.springframework.test.util.ReflectionTestUtils.setField(a, "unlockTime", 1600000000L);
                dtoList.add(a);
            }
            for (int i = 8; i < 10; i++) {
                var a = new SteamPlayerAchievementsResponseDto.AchievementDto();
                org.springframework.test.util.ReflectionTestUtils.setField(a, "apiName", "ACH_" + i);
                org.springframework.test.util.ReflectionTestUtils.setField(a, "achieved", 0);
                org.springframework.test.util.ReflectionTestUtils.setField(a, "unlockTime", 0L);
                dtoList.add(a);
            }
            org.springframework.test.util.ReflectionTestUtils.setField(stats, "achievements", dtoList);
            org.springframework.test.util.ReflectionTestUtils.setField(stats, "success", true);

            given(steamApiClient.getPlayerAchievements(testUser.getSteamId(), 1091500L))
                    .willReturn(Optional.of(stats));

            // when
            var response = steamSyncService.syncUserGameAchievements(testUser, 1091500L);

            // then
            assertThat(response.getAppId()).isEqualTo(1091500L);
            assertThat(response.getTotalAchievements()).isEqualTo(10);
            assertThat(response.getAchievedAchievements()).isEqualTo(8);
            assertThat(response.getAchievementRate()).isEqualTo(80.0);
            assertThat(response.getAchievements()).hasSize(10);
            assertThat(userGame.getAchievementRate()).isEqualTo(80.0);
        }

        @Test
        @DisplayName("성공: 스팀에서 업적이 없거나 비공개인 경우 total=0, rate=null로 갱신된다")
        void syncUserGameAchievements_NoAchievements_Success() {
            // given
            Game game = Game.builder().appId(20L).name("No Achievement Game").build();
            UserGame userGame = UserGame.builder().id(2L).user(testUser).game(game).build();

            given(userGameRepository.findByUserAndGame_AppId(testUser, 20L))
                    .willReturn(Optional.of(userGame));
            given(steamApiClient.getPlayerAchievements(testUser.getSteamId(), 20L))
                    .willReturn(Optional.empty());

            // when
            var response = steamSyncService.syncUserGameAchievements(testUser, 20L);

            // then
            assertThat(response.getTotalAchievements()).isEqualTo(0);
            assertThat(response.getAchievedAchievements()).isEqualTo(0);
            assertThat(response.getAchievementRate()).isNull();
            assertThat(userGame.getTotalAchievements()).isEqualTo(0);
            assertThat(userGame.getAchievementRate()).isNull();
        }

        @Test
        @DisplayName("성공: 60분 이내에 이미 동기화된 게임은 스팀 API를 다시 호출하지 않고 캐시를 반환한다")
        void syncUserGameAchievements_Within60Minutes_ReturnsCached_WithoutCallingSteamApi() {
            // given
            Game game = Game.builder().appId(1091500L).name("Cyberpunk 2077").build();
            LocalDateTime lastPlayed = LocalDateTime.now().minusDays(1);
            UserGame userGame = UserGame.builder()
                    .id(1L)
                    .user(testUser)
                    .game(game)
                    .playtimeForever(7200)
                    .lastPlayedAt(lastPlayed)
                    .totalAchievements(50)
                    .achievedAchievements(40)
                    .achievementRate(80.0)
                    .achievementsSyncedAt(LocalDateTime.now().minusMinutes(20)) // 20분 전 동기화
                    .build();

            given(userGameRepository.findByUserAndGame_AppId(testUser, 1091500L))
                    .willReturn(Optional.of(userGame));

            // when
            var response = steamSyncService.syncUserGameAchievements(testUser, 1091500L, false);

            // then
            assertThat(response.getAppId()).isEqualTo(1091500L);
            assertThat(response.getTotalAchievements()).isEqualTo(50);
            assertThat(response.getAchievedAchievements()).isEqualTo(40);
            assertThat(response.getAchievementRate()).isEqualTo(80.0);
            assertThat(response.getPlaytimeForever()).isEqualTo(7200);
            assertThat(response.getLastPlayedAt()).isEqualTo(lastPlayed);

            // 스팀 외부 API 호출이 0회여야 함 (캐시 방어 성공)
            verify(steamApiClient, never()).getPlayerAchievements(anyString(), anyLong());
        }
    }
}
