package com.steamfolder.steamfolder.domain.game.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SteamApiClient {

    public static final String DEFAULT_STEAM_API_BASE_URL = "https://api.steampowered.com";
    public static final String DEFAULT_STEAM_STORE_BASE_URL = "https://store.steampowered.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern LOG_CREDENTIAL = Pattern.compile(
            "(?i)([\"']?(?:key|api[_-]?key|token|access[_-]?token|refresh[_-]?token|authorization|password|secret)[\"']?\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|[^\\s&<>]+)");
    private static final Pattern LOG_BEARER = Pattern.compile("(?i)Bearer\\s+[^\\s\"'<>,]+");
    private static final Pattern LOG_JWT = Pattern.compile("[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}");

    private final WebClient webClient;
    private final String apiKey;
    private final String steamApiBaseUrl;
    private final String steamStoreBaseUrl;
    private final ObjectMapper objectMapper;
    private final Duration achievementTimeout;

    @Autowired
    public SteamApiClient(
            WebClient.Builder webClientBuilder,
            @Value("${steam.api.key:}") String apiKey,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClientBuilder.build();
        this.apiKey = apiKey;
        this.steamApiBaseUrl = DEFAULT_STEAM_API_BASE_URL;
        this.steamStoreBaseUrl = DEFAULT_STEAM_STORE_BASE_URL;
        this.objectMapper = objectMapper;
        this.achievementTimeout = TIMEOUT;
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
        return createForTest(webClientBuilder, apiKey, steamApiBaseUrl, steamStoreBaseUrl, TIMEOUT);
    }

    static SteamApiClient createForTest(WebClient.Builder builder, String apiKey, String apiBaseUrl,
                                       String storeBaseUrl, Duration achievementTimeout) {
        return new SteamApiClient(builder, apiKey, apiBaseUrl, storeBaseUrl, achievementTimeout);
    }

    private SteamApiClient(
            WebClient.Builder webClientBuilder,
            String apiKey,
            String steamApiBaseUrl,
            String steamStoreBaseUrl,
            Duration achievementTimeout
    ) {
        this.webClient = webClientBuilder.build();
        this.apiKey = apiKey;
        this.steamApiBaseUrl = steamApiBaseUrl;
        this.steamStoreBaseUrl = steamStoreBaseUrl;
        this.objectMapper = new ObjectMapper();
        this.achievementTimeout = achievementTimeout;
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
     * - 확인된 no-stats 응답만 Optional.empty(), 비공개는 G004, 나머지 실패는 G003.
     */
    public Optional<SteamPlayerAchievementsResponseDto.PlayerStats> getPlayerAchievements(String steamId, Long appId) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("Steam achievements appId={} reason=MISSING_API_KEY", appId);
            throw new CustomException(ErrorCode.STEAM_API_ERROR);
        }

        AtomicReference<AchievementHttpMetadata> metadata = new AtomicReference<>(
                new AchievementHttpMetadata(null, null));
        try {
            return webClient.get()
                    .uri(steamApiBaseUrl + "/ISteamUserStats/GetPlayerAchievements/v0001/?key={apiKey}&steamid={steamId}&appid={appId}&l=korean",
                            apiKey, steamId, appId)
                    .exchangeToMono(response -> {
                        AchievementHttpMetadata received = new AchievementHttpMetadata(response.statusCode().value(),
                                response.headers().asHttpHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
                        metadata.set(received);
                        // 오류 JSON이 text/plain 등으로 오는 경우에도 본문을 검증한다. HTML/잘못된 JSON은 실패다.
                        return response.bodyToMono(String.class).defaultIfEmpty("")
                                .map(body -> parseAchievements(received, body, appId));
                    })
                    .timeout(achievementTimeout)
                    .block();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            AchievementHttpMetadata received = metadata.get();
            log.warn("Steam achievements appId={} reason={} httpStatus={} contentType={} exceptionTypes={}",
                    appId, achievementFailureReason(e), received.status(), safeLogValue(received.contentType(), 128),
                    exceptionTypes(e));
            throw new CustomException(ErrorCode.STEAM_API_ERROR);
        }
    }

    private Optional<SteamPlayerAchievementsResponseDto.PlayerStats> parseAchievements(
            AchievementHttpMetadata http, String body, Long appId) {
        int status = http.status();
        // 빈 403은 기존 G004 정책을 유지하되, 키 오류 등 본문이 있는 미확인 403은 G003이다.
        if (status == 403 && body.isBlank()) {
            throw achievementError(ErrorCode.STEAM_PROFILE_PRIVATE, "PRIVATE_PROFILE", http, body, appId, null);
        }
        if (status != 200 && status != 400 && status != 403) {
            throw achievementError(ErrorCode.STEAM_API_ERROR, "HTTP_ERROR", http, body, appId, null);
        }
        if (body.isBlank()) {
            throw achievementError(ErrorCode.STEAM_API_ERROR, "EMPTY_BODY", http, body, appId, null);
        }
        try {
            JsonNode root = objectMapper.readerFor(JsonNode.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(body);
            JsonNode stats = root == null ? null : root.get("playerstats");
            if (stats == null || !stats.isObject() || !stats.path("success").isBoolean()) {
                throw achievementError(ErrorCode.STEAM_API_ERROR, "INVALID_RESPONSE", http, body, appId, null);
            }
            if (!stats.get("success").booleanValue()) {
                String error = stats.path("error").asText("");
                if (isPrivateProfileError(error)) {
                    throw achievementError(ErrorCode.STEAM_PROFILE_PRIVATE, "PRIVATE_PROFILE", http, body, appId, null);
                }
                if ((status == 200 || status == 400) && "Requested app has no stats".equalsIgnoreCase(error.trim())) {
                    log.info("Steam achievements appId={} reason=NO_STATS httpStatus={} contentType={} body={}",
                            appId, status, safeLogValue(http.contentType(), 128), safeLogValue(body, 1024));
                    return Optional.empty();
                }
                throw achievementError(ErrorCode.STEAM_API_ERROR, "STEAM_REJECTED", http, body, appId, null);
            }
            if (status != 200 || !stats.path("achievements").isArray()) {
                throw achievementError(ErrorCode.STEAM_API_ERROR, "INVALID_RESPONSE", http, body, appId, null);
            }
            for (JsonNode achievement : stats.get("achievements")) {
                JsonNode achieved = achievement.path("achieved");
                if (!achievement.path("apiname").isTextual() || achievement.get("apiname").asText().isBlank()
                        || !achieved.isIntegralNumber() || !achieved.canConvertToInt()
                        || (achieved.intValue() != 0 && achieved.intValue() != 1)) {
                    throw achievementError(ErrorCode.STEAM_API_ERROR, "INVALID_RESPONSE", http, body, appId, null);
                }
            }
            return Optional.of(objectMapper.treeToValue(stats, SteamPlayerAchievementsResponseDto.PlayerStats.class));
        } catch (JsonProcessingException e) {
            throw achievementError(ErrorCode.STEAM_API_ERROR, "DECODING_FAILURE", http, body, appId,
                    e.getClass().getSimpleName());
        }
    }

    private CustomException achievementError(ErrorCode code, String reason, AchievementHttpMetadata http,
                                             String body, Long appId, String exceptionType) {
        // 예외 메시지/스택/요청 URI에는 key 쿼리가 포함될 수 있어 기록하지 않는다.
        log.warn("Steam achievements appId={} reason={} httpStatus={} contentType={} body={} exceptionType={}",
                appId, reason, http.status(), safeLogValue(http.contentType(), 128), safeLogValue(body, 1024),
                exceptionType);
        return new CustomException(code);
    }

    private String safeLogValue(String value, int limit) {
        if (value == null) return "<missing>";
        String safe = value.replace(apiKey, "[REDACTED]");
        safe = LOG_BEARER.matcher(safe).replaceAll("Bearer [REDACTED]");
        safe = LOG_CREDENTIAL.matcher(safe).replaceAll("$1[REDACTED]");
        safe = LOG_JWT.matcher(safe).replaceAll("[REDACTED]");
        safe = safe.replaceAll("\\p{Cntrl}", " ");
        return safe.length() > limit ? safe.substring(0, limit) + "...[truncated]" : safe;
    }

    private String achievementFailureReason(Throwable error) {
        boolean networkFailure = false;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause.getClass().getSimpleName().endsWith("TimeoutException")) {
                return "TIMEOUT";
            }
            if (cause instanceof DecodingException) return "DECODING_FAILURE";
            if (cause instanceof WebClientRequestException || cause instanceof java.io.IOException) {
                networkFailure = true;
            }
        }
        return networkFailure ? "NETWORK_FAILURE" : "BODY_READ_OR_UNEXPECTED_FAILURE";
    }

    private String exceptionTypes(Throwable error) {
        StringBuilder types = new StringBuilder();
        for (Throwable cause = error; cause != null && types.length() < 256; cause = cause.getCause()) {
            if (!types.isEmpty()) types.append(" -> ");
            types.append(cause.getClass().getSimpleName());
        }
        return types.toString();
    }

    private record AchievementHttpMetadata(Integer status, String contentType) {}

    private boolean isPrivateProfileError(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return false;
        }

        String normalizedMessage = errorMessage.toLowerCase(Locale.ROOT);
        return normalizedMessage.contains("private") || normalizedMessage.contains("not public");
    }
}
