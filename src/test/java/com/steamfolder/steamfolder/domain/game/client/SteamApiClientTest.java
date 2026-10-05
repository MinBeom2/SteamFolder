package com.steamfolder.steamfolder.domain.game.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SteamApiClientTest {

    private MockWebServer mockWebServer;
    private SteamApiClient steamApiClient;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("").toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        steamApiClient = SteamApiClient.createForTest(WebClient.builder(), "test-api-key", baseUrl, baseUrl);
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(SteamApiClient.class)).addAppender(logAppender);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
        ((Logger) LoggerFactory.getLogger(SteamApiClient.class)).detachAppender(logAppender);
        logAppender.stop();
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
    @DisplayName("빈 400은 업적 없음의 근거가 아니므로 G003으로 유지한다")
    void getPlayerAchievements_Empty400_ThrowsSteamApiError() {
        // given
        mockWebServer.enqueue(new MockResponse().setResponseCode(400));

        assertSteamApiError();
    }

    @Test
    @DisplayName("Steam GetPlayerAchievements 403 비공개 응답 시 G004 예외 변환 검증")
    void getPlayerAchievements_PrivateProfile_ThrowsSteamProfilePrivate() {
        // given
        mockWebServer.enqueue(new MockResponse().setResponseCode(403));

        // when & then
        assertThatThrownBy(() -> steamApiClient.getPlayerAchievements("76561198000000001", 1091500L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STEAM_PROFILE_PRIVATE);
    }

    @Test
    @DisplayName("Steam GetPlayerAchievements 400 실패 본문이 비공개를 나타내면 G004 예외 변환 검증")
    void getPlayerAchievements_PrivateProfileBody_ThrowsSteamProfilePrivate() {
        // given
        String jsonResponse = """
                {
                    "playerstats": {
                        "success": false,
                        "error": "Profile is not public"
                    }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(jsonResponse));

        // when & then
        assertThatThrownBy(() -> steamApiClient.getPlayerAchievements("76561198000000001", 1091500L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STEAM_PROFILE_PRIVATE);
    }

    @Test
    @DisplayName("Steam GetPlayerAchievements 500 서버 오류 시 G003 예외 변환 검증")
    void getPlayerAchievements_ServerError_ThrowsSteamApiError() {
        // given
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        // when & then
        assertThatThrownBy(() -> steamApiClient.getPlayerAchievements("76561198000000001", 1091500L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STEAM_API_ERROR);
    }

    @Test
    @DisplayName("appId 2688840에서 실제 관측한 HTTP400 JSON no-stats 본문을 처리한다")
    void getPlayerAchievements_Observed2688840NoStats_ReturnsEmpty() throws IOException {
        // 2026-10-04 실제 Steam 응답: HTTP400, application/json; charset=UTF-8.
        mockWebServer.enqueue(new MockResponse().setResponseCode(400)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8")
                .setBody(observedNoStatsBody()));

        assertThat(steamApiClient.getPlayerAchievements("76561198000000001", 2688840L)).isEmpty();
        assertThat(logMessages()).contains("reason=NO_STATS", "httpStatus=400",
                "contentType=application/json; charset=UTF-8", "Requested app has no stats");
    }

    @ParameterizedTest
    @MethodSource("noStatsResponses")
    @DisplayName("확인된 no-stats JSON은 HTTP200/400 및 Content-Type 차이에도 0개로 분류한다")
    void getPlayerAchievements_NoStatsWithDifferentContentTypes_ReturnsEmpty(int status, String contentType)
            throws IOException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(status)
                .setHeader(HttpHeaders.CONTENT_TYPE, contentType).setBody(observedNoStatsBody()));

        assertThat(steamApiClient.getPlayerAchievements("76561198000000001", 2688840L)).isEmpty();
    }

    static Stream<Arguments> noStatsResponses() {
        return Stream.of(200, 400).flatMap(status -> Stream.of("application/json", "text/plain", "text/html")
                .map(contentType -> Arguments.of(status, contentType)));
    }

    @Test
    @DisplayName("정상 success=true 및 빈 업적 배열은 유효한 0개 응답이다")
    void getPlayerAchievements_ValidZero_ReturnsStats() {
        enqueueAchievements(200, """
                {"playerstats":{"success":true,"achievements":[]}}
                """);

        var stats = steamApiClient.getPlayerAchievements("76561198000000001", 2688840L).orElseThrow();
        assertThat(stats.isSuccess()).isTrue();
        assertThat(stats.getAchievements()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 403})
    @DisplayName("본문으로 확인된 비공개 프로필은 G004 정책을 유지한다")
    void getPlayerAchievements_ConfirmedPrivate_ThrowsG004(int status) {
        enqueueAchievements(status, """
                {"playerstats":{"success":false,"error":"Profile is not public"}}
                """);
        assertThatThrownBy(() -> steamApiClient.getPlayerAchievements("76561198000000001", 2688840L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STEAM_PROFILE_PRIVATE);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 403, 500, 503})
    @DisplayName("키 거부 등 미확인 success=false 오류는 업적 없음으로 분류하지 않는다")
    void getPlayerAchievements_UnknownFailure_ThrowsG003(int status) {
        enqueueAchievements(status, """
                {"playerstats":{"success":false,"error":"Invalid API key"}}
                """);
        assertSteamApiError();
    }

    @Test
    @DisplayName("사용자 통계 조회 실패는 게임 전체의 업적 없음과 구분한다")
    void getPlayerAchievements_PlayerStatsUnavailable_ThrowsG003() {
        enqueueAchievements(400, """
                {"playerstats":{"success":false,"error":"Failed to retrieve data for the player"}}
                """);
        assertSteamApiError();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    @DisplayName("HTTP 장애 응답에 no-stats 문자열이 있어도 정상 0개로 바꾸지 않는다")
    void getPlayerAchievements_HttpFailureWithNoStatsBody_ThrowsG003(int status) throws IOException {
        enqueueAchievements(status, observedNoStatsBody());
        assertSteamApiError();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "{}", "{\"playerstats\":null}",
            "{\"playerstats\":{\"achievements\":[]}}",
            "{\"playerstats\":{\"success\":false}}",
            "{\"playerstats\":{\"success\":true}}",
            "{\"playerstats\":{\"success\":true,\"achievements\":null}}",
            "{\"playerstats\":{\"success\":\"true\",\"achievements\":[]}}",
            "{\"playerstats\":{\"success\":true,\"achievements\":[null]}}",
            "{\"playerstats\":{\"success\":true,\"achievements\":[{\"apiname\":\"ACH_1\",\"achieved\":2}]}}",
            "{\"playerstats\":{\"success\":true,\"achievements\":[]}} {}",
            "{broken json}", "<html>Bad gateway</html>"
    })
    @DisplayName("빈 본문, 잘못된 스키마, 디코딩 실패는 G003이며 0개로 저장되지 않는다")
    void getPlayerAchievements_InvalidResponse_ThrowsG003(String body) {
        enqueueAchievements(200, body);
        assertSteamApiError();
    }

    @Test
    @DisplayName("키 누락은 외부 호출 없이 G003을 반환한다")
    void getPlayerAchievements_MissingKey_ThrowsG003WithoutRequest() {
        steamApiClient = SteamApiClient.createForTest(WebClient.builder(), "", "unused", "unused");
        assertSteamApiError();
        assertThat(mockWebServer.getRequestCount()).isZero();
        assertThat(logMessages()).contains("reason=MISSING_API_KEY");
    }

    @Test
    @DisplayName("실제 응답 지연은 TIMEOUT으로 진단하고 G003을 유지한다")
    void getPlayerAchievements_Timeout_ThrowsG003() {
        String baseUrl = mockWebServer.url("/").toString();
        steamApiClient = SteamApiClient.createForTest(WebClient.builder(), "test-api-key", baseUrl, baseUrl,
                Duration.ofMillis(100));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{\"playerstats\":{\"success\":true,\"achievements\":[]}}")
                .setBodyDelay(1, TimeUnit.SECONDS));

        assertSteamApiError();
        assertThat(logMessages()).contains("reason=TIMEOUT", "TimeoutException");
        assertThat(logAppender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    }

    @Test
    @DisplayName("디코딩 오류 로그에 HTTP 상태, Content-Type, 가려진 본문과 예외 종류를 남긴다")
    void getPlayerAchievements_DecodingFailure_LogsSafeDiagnostics() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(400)
                .setHeader(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .setBody("<html>key=test-api-key&access_token=secret-token</html>"));
        assertSteamApiError();

        assertThat(logMessages()).contains("reason=DECODING_FAILURE", "httpStatus=400",
                "contentType=text/html; charset=UTF-8", "JsonParseException", "[REDACTED]")
                .doesNotContain("test-api-key", "secret-token");
    }

    @Test
    @DisplayName("HTTP 오류 본문의 API 키와 인증 토큰을 로그에서 제외한다")
    void getPlayerAchievements_UpstreamFailure_RedactsCredentials() {
        enqueueAchievements(503, """
                {"key":"test-api-key","access_token":"secret-access-token",
                 "refresh_token":"secret-refresh-token","Authorization":"Bearer secret-auth-token",
                 "error":"echo test-api-key"}
                """);
        assertSteamApiError();

        assertThat(logMessages()).contains("reason=HTTP_ERROR", "httpStatus=503", "[REDACTED]")
                .doesNotContain("test-api-key", "secret-access-token", "secret-refresh-token", "secret-auth-token");
        assertThat(logAppender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    }

    @Test
    @DisplayName("네트워크 예외의 메시지와 URI에 있는 키·토큰은 로그에 남기지 않는다")
    void getPlayerAchievements_NetworkFailure_LogsExceptionTypesOnly() {
        steamApiClient = SteamApiClient.createForTest(WebClient.builder()
                .exchangeFunction(request -> Mono.error(new IOException("key=test-api-key Bearer secret-token"))),
                "test-api-key", "https://api.steampowered.com", "unused");
        assertSteamApiError();

        assertThat(logMessages()).contains("reason=NETWORK_FAILURE", "IOException")
                .doesNotContain("test-api-key", "secret-token", "https://api.steampowered.com");
        assertThat(logAppender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    }

    private void enqueueAchievements(int status, String body) {
        mockWebServer.enqueue(new MockResponse().setResponseCode(status)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).setBody(body));
    }

    private void assertSteamApiError() {
        assertThatThrownBy(() -> steamApiClient.getPlayerAchievements("76561198000000001", 2688840L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STEAM_API_ERROR);
    }

    private String observedNoStatsBody() throws IOException {
        try (var stream = getClass().getResourceAsStream("/steam/2688840-no-stats.json")) {
            return new String(java.util.Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }

    private String logMessages() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
