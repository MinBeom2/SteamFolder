package com.steamfolder.steamfolder.domain.game.client;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamAppDetailsResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamOwnedGamesResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamPlayerAchievementsResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamPlayerSummaryResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class SteamApiClient {

    public static final String DEFAULT_STEAM_API_BASE_URL = "https://api.steampowered.com";
    public static final String DEFAULT_STEAM_STORE_BASE_URL = "https://store.steampowered.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final String apiKey;
    private final String steamApiBaseUrl;
    private final String steamStoreBaseUrl;

    @Autowired
    public SteamApiClient(
            WebClient.Builder webClientBuilder,
            @Value("${steam.api.key:}") String apiKey
    ) {
        this.webClient = webClientBuilder.build();
        this.apiKey = apiKey;
        this.steamApiBaseUrl = DEFAULT_STEAM_API_BASE_URL;
        this.steamStoreBaseUrl = DEFAULT_STEAM_STORE_BASE_URL;
    }

    /**
     * 단위 테스트 전용 생성자 (MockWebServer Base URL 주입용)
     */
    public static SteamApiClient createForTest(
            WebClient.Builder webClientBuilder,
            String apiKey,
            String steamApiBaseUrl,
            String steamStoreBaseUrl
    ) {
        return new SteamApiClient(webClientBuilder, apiKey, steamApiBaseUrl, steamStoreBaseUrl, true);
    }

    private SteamApiClient(
            WebClient.Builder webClientBuilder,
            String apiKey,
            String steamApiBaseUrl,
            String steamStoreBaseUrl,
            boolean isTest
    ) {
        this.webClient = webClientBuilder.build();
        this.apiKey = apiKey;
        this.steamApiBaseUrl = steamApiBaseUrl;
        this.steamStoreBaseUrl = steamStoreBaseUrl;
    }

    /**
     * 유저의 보유 게임 목록 및 플레이타임 조회 (GetOwnedGames)
     */
    public SteamOwnedGamesResponseDto.ResponseData getUserOwnedGames(String steamId) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("Steam Web API Key is missing. Skipping Steam API call for getUserOwnedGames.");
            throw new CustomException(ErrorCode.STEAM_API_ERROR);
        }

        try {
            SteamOwnedGamesResponseDto response = webClient.get()
                    .uri(steamApiBaseUrl + "/IPlayerService/GetOwnedGames/v0001/"
                            + "?key={key}&steamid={steamId}&format=json&include_appinfo=true&include_played_free_games=true",
                            apiKey, steamId)
                    .retrieve()
                    .onStatus(status -> status.value() == 403, clientResponse ->
                            Mono.error(new CustomException(ErrorCode.STEAM_PROFILE_PRIVATE)))
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            Mono.error(new CustomException(ErrorCode.STEAM_API_ERROR)))
                    .bodyToMono(SteamOwnedGamesResponseDto.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null || response.getResponse() == null) {
                return new SteamOwnedGamesResponseDto.ResponseData();
            }

            return response.getResponse();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch owned games from Steam API for steamId: {}", steamId, e);
            throw new CustomException(ErrorCode.STEAM_API_ERROR);
        }
    }

    /**
     * 유저 프로필 요약 및 공개 상태 조회 (GetPlayerSummaries)
     */
    public Optional<SteamPlayerSummaryResponseDto.PlayerDto> getPlayerSummary(String steamId) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("Steam Web API Key is missing. Skipping Steam API call for getPlayerSummary.");
            return Optional.empty();
        }

        try {
            SteamPlayerSummaryResponseDto response = webClient.get()
                    .uri(steamApiBaseUrl + "/ISteamUser/GetPlayerSummaries/v0002/"
                            + "?key={key}&steamids={steamId}",
                            apiKey, steamId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            Mono.empty())
                    .bodyToMono(SteamPlayerSummaryResponseDto.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null || response.getResponse() == null || response.getResponse().getPlayers().isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(response.getResponse().getPlayers().get(0));
        } catch (Exception e) {
            log.warn("Failed to fetch player summary from Steam API for steamId: {}. Using default.", steamId, e);
            return Optional.empty();
        }
    }

    /**
     * 스팀 상점 게임 상세 메타데이터 조회 (appdetails)
     */
    public Optional<SteamAppDetailsResponseDto.AppData> getAppDetails(Long appId) {
        try {
            Map<String, SteamAppDetailsResponseDto> responseMap = webClient.get()
                    .uri(steamStoreBaseUrl + "/api/appdetails?appids={appId}&l=korean", appId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            Mono.empty())
                    .bodyToMono(new ParameterizedTypeReference<Map<String, SteamAppDetailsResponseDto>>() {})
                    .timeout(TIMEOUT)
                    .block();

            if (responseMap == null || !responseMap.containsKey(String.valueOf(appId))) {
                return Optional.empty();
            }

            SteamAppDetailsResponseDto appDetails = responseMap.get(String.valueOf(appId));
            if (appDetails == null || !appDetails.isSuccess() || appDetails.getData() == null) {
                return Optional.empty();
            }

            return Optional.of(appDetails.getData());
        } catch (Exception e) {
            log.warn("Failed to fetch app details from Steam Store API for appId: {}. Using fallback.", appId, e);
            return Optional.empty();
        }
    }

    /**
     * 유저의 특정 게임 도전과제(업적) 달성 현황 조회 (GetPlayerAchievements)
     * - 게임에 도전과제가 없거나(400 에러) 비공개 프로필(403 등)인 경우 Optional.empty()로 안전하게 예외 격리
     */
    public Optional<SteamPlayerAchievementsResponseDto.PlayerStats> getPlayerAchievements(String steamId, Long appId) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Steam API Key is missing. Skipping getPlayerAchievements for steamId: {}, appId: {}", steamId, appId);
            throw new CustomException(ErrorCode.STEAM_API_ERROR);
        }

        try {
            SteamPlayerAchievementsResponseDto response = webClient.get()
                    .uri(steamApiBaseUrl + "/ISteamUserStats/GetPlayerAchievements/v0001/?key={apiKey}&steamid={steamId}&appid={appId}&l=korean",
                            apiKey, steamId, appId)
                    .retrieve()
                    .onStatus(status -> status.value() == 403, clientResponse ->
                            Mono.error(new CustomException(ErrorCode.STEAM_PROFILE_PRIVATE)))
                    .onStatus(status -> status.value() == 400, clientResponse -> Mono.empty())
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            Mono.error(new CustomException(ErrorCode.STEAM_API_ERROR)))
                    .bodyToMono(SteamPlayerAchievementsResponseDto.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null || response.getPlayerStats() == null) {
                return Optional.empty();
            }

            SteamPlayerAchievementsResponseDto.PlayerStats playerStats = response.getPlayerStats();
            if (!playerStats.isSuccess()) {
                if (isPrivateProfileError(playerStats.getError())) {
                    throw new CustomException(ErrorCode.STEAM_PROFILE_PRIVATE);
                }
                return Optional.empty();
            }

            return Optional.of(playerStats);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch player achievements from Steam API for steamId: {}, appId: {}", steamId, appId, e);
            throw new CustomException(ErrorCode.STEAM_API_ERROR);
        }
    }

    private boolean isPrivateProfileError(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return false;
        }

        String normalizedMessage = errorMessage.toLowerCase(Locale.ROOT);
        return normalizedMessage.contains("private") || normalizedMessage.contains("not public");
    }
}
