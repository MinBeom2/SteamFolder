package com.steamfolder.steamfolder.domain.game.client;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamAppDetailsResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamOwnedGamesResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamPlayerSummaryResponseDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SteamApiClientTest {

    private MockWebServer mockWebServer;
    private SteamApiClient steamApiClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("").toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        steamApiClient = SteamApiClient.createForTest(WebClient.builder(), "test-api-key", baseUrl, baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Steam GetOwnedGames API 정상 호출 및 파싱 검증")
    void getUserOwnedGames_Success() {
        // given
        String jsonResponse = """
                {
                    "response": {
                        "game_count": 2,
                        "games": [
                            {
                                "appid": 730,
                                "name": "Counter-Strike 2",
                                "playtime_forever": 1500,
                                "img_icon_url": "icon_hash_1",
                                "rtime_last_played": 1700000000
                            },
                            {
                                "appid": 570,
                                "name": "Dota 2",
                                "playtime_forever": 3600,
                                "img_icon_url": "icon_hash_2",
                                "rtime_last_played": 1700050000
                            }
                        ]
                    }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(jsonResponse));

        // when
        SteamOwnedGamesResponseDto.ResponseData response = steamApiClient.getUserOwnedGames("76561198000000001");

        // then
        assertThat(response).isNotNull();
        assertThat(response.getGameCount()).isEqualTo(2);
        assertThat(response.getGames()).hasSize(2);
        assertThat(response.getGames().get(0).getAppId()).isEqualTo(730L);
        assertThat(response.getGames().get(0).getName()).isEqualTo("Counter-Strike 2");
        assertThat(response.getGames().get(0).getPlaytimeForever()).isEqualTo(1500);
    }

    @Test
    @DisplayName("Steam GetPlayerSummaries API 정상 호출 및 공개 프로필 검증")
    void getPlayerSummary_Success() {
        // given
        String jsonResponse = """
                {
                    "response": {
                        "players": [
                            {
                                "steamid": "76561198000000001",
                                "personaname": "Gamer123",
                                "avatarfull": "https://avatars.steamstatic.com/avatar_full.jpg",
                                "communityvisibilitystate": 3
                            }
                        ]
                    }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(jsonResponse));

        // when
        Optional<SteamPlayerSummaryResponseDto.PlayerDto> playerOpt = steamApiClient.getPlayerSummary("76561198000000001");

        // then
        assertThat(playerOpt).isPresent();
        SteamPlayerSummaryResponseDto.PlayerDto player = playerOpt.get();
        assertThat(player.getSteamId()).isEqualTo("76561198000000001");
        assertThat(player.getPersonaName()).isEqualTo("Gamer123");
        assertThat(player.isPublic()).isTrue();
    }

    @Test
    @DisplayName("Steam Store appdetails API 메타데이터(장르, 개발사, 이미지) 파싱 검증")
    void getAppDetails_Success() {
        // given
        String jsonResponse = """
                {
                    "730": {
                        "success": true,
                        "data": {
                            "steam_appid": 730,
                            "name": "Counter-Strike 2",
                            "header_image": "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/header.jpg",
                            "background": "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/page_bg.jpg",
                            "developers": ["Valve"],
                            "genres": [
                                {"id": "1", "description": "Action"},
                                {"id": "37", "description": "Free to Play"}
                            ],
                            "release_date": {
                                "coming_soon": false,
                                "date": "2012-08-21"
                            }
                        }
                    }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(jsonResponse));

        // when
        Optional<SteamAppDetailsResponseDto.AppData> appDataOpt = steamApiClient.getAppDetails(730L);

        // then
        assertThat(appDataOpt).isPresent();
        SteamAppDetailsResponseDto.AppData appData = appDataOpt.get();
        assertThat(appData.getSteamAppId()).isEqualTo(730L);
        assertThat(appData.getName()).isEqualTo("Counter-Strike 2");
        assertThat(appData.getDeveloperName()).isEqualTo("Valve");
        assertThat(appData.getGenreNames()).containsExactly("Action", "Free to Play");
        assertThat(appData.getReleaseDateString()).isEqualTo("2012-08-21");
    }

    @Test
    @DisplayName("Steam API 500 서버 에러 발생 시 STEAM_API_ERROR 예외 변환 검증")
    void getUserOwnedGames_ServerError_ThrowsCustomException() {
        // given
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        // when & then
        assertThatThrownBy(() -> steamApiClient.getUserOwnedGames("76561198000000001"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STEAM_API_ERROR);
    }

    @Test
    @DisplayName("Steam GetPlayerAchievements API 정상 호출 및 업적 파싱 검증")
    void getPlayerAchievements_Success() {
        // given
        String jsonResponse = """
                {
                    "playerstats": {
                        "steamID": "76561198000000001",
                        "gameName": "Cyberpunk 2077",
                        "achievements": [
                            {
                                "apiname": "ACH_01",
                                "achieved": 1,
                                "unlocktime": 1607644800
                            },
                            {
                                "apiname": "ACH_02",
                                "achieved": 0,
                                "unlocktime": 0
                            }
                        ],
                        "success": true
                    }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(jsonResponse));

        // when
        var statsOpt = steamApiClient.getPlayerAchievements("76561198000000001", 1091500L);

        // then
        assertThat(statsOpt).isPresent();
        var stats = statsOpt.get();
        assertThat(stats.getGameName()).isEqualTo("Cyberpunk 2077");
        assertThat(stats.getAchievements()).hasSize(2);
        assertThat(stats.getAchievements().get(0).getApiName()).isEqualTo("ACH_01");
        assertThat(stats.getAchievements().get(0).getAchieved()).isEqualTo(1);
    }

    @Test
    @DisplayName("Steam GetPlayerAchievements 400 에러(도전과제 없음) 발생 시 Optional.empty 반환 검증")
    void getPlayerAchievements_NoStats_ReturnsEmpty() {
        // given
        mockWebServer.enqueue(new MockResponse().setResponseCode(400));

        // when
        var statsOpt = steamApiClient.getPlayerAchievements("76561198000000001", 999999L);

        // then
        assertThat(statsOpt).isEmpty();
    }
}
