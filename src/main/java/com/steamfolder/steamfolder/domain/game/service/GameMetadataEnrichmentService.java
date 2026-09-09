package com.steamfolder.steamfolder.domain.game.service;

import com.steamfolder.steamfolder.common.config.AsyncConfig;
import com.steamfolder.steamfolder.domain.game.client.SteamApiClient;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamAppDetailsResponseDto;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameMetadataEnrichmentService {

    private final SteamApiClient steamApiClient;
    private final GameRepository gameRepository;

    private static final long DELAY_BETWEEN_CALLS_MS = 500L;
    private final Set<Long> processingAppIds = ConcurrentHashMap.newKeySet();

    /**
     * 비동기 백그라운드 메타데이터(장르 등) 수집 워커
     * - 전용 단일 스레드 풀(gameEnrichmentExecutor)에서 순차 실행
     * - 스팀 429 Rate Limit 차단을 원천 방지하기 위해 각 API 호출 간 500ms 안전 딜레이 적용
     * - 중복 호출 방지를 위한 Set 기반 필터링
     */
    @Async(AsyncConfig.GAME_ENRICHMENT_EXECUTOR)
    public void enrichGameMetadataAsync(Collection<Long> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return;
        }

        List<Long> targetAppIds = appIds.stream()
                .filter(id -> id != null && !processingAppIds.contains(id))
                .distinct()
                .toList();

        if (targetAppIds.isEmpty()) {
            return;
        }

        log.info("스팀 게임 메타데이터 비동기 보강 작업 시작 (대상 게임 수: {}개)", targetAppIds.size());

        for (Long appId : targetAppIds) {
            if (!processingAppIds.add(appId)) {
                continue;
            }

            try {
                enrichSingleGame(appId);

                // 스팀 상점 API Rate Limit 방어 (500ms 딜레이)
                try {
                    Thread.sleep(DELAY_BETWEEN_CALLS_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("비동기 메타데이터 수집 중 쓰레드 인터럽트 발생");
                    break;
                }
            } finally {
                processingAppIds.remove(appId);
            }
        }

        log.info("스팀 게임 메타데이터 비동기 보강 작업 완료 (처리 대상: {}개)", targetAppIds.size());
    }

    /**
     * 단일 게임 메타데이터 조회 및 DB 반영
     */
    public void enrichSingleGame(Long appId) {
        Optional<Game> gameOpt = gameRepository.findById(appId);
        if (gameOpt.isEmpty()) {
            return;
        }

        Game game = gameOpt.get();
        // 이미 장르 정보가 채워져 있다면 외부 API 호출 생략
        if (game.getGenres() != null && !game.getGenres().isEmpty()) {
            return;
        }

        try {
            Optional<SteamAppDetailsResponseDto.AppData> appDetailsOpt = steamApiClient.getAppDetails(appId);
            if (appDetailsOpt.isPresent()) {
                SteamAppDetailsResponseDto.AppData appData = appDetailsOpt.get();
                List<String> genreNames = appData.getGenreNames();

                game.updateMetadata(
                        appData.getName(),
                        appData.getHeaderImage(),
                        appData.getBackground(),
                        genreNames,
                        appData.getDeveloperName(),
                        appData.getReleaseDateString()
                );
                gameRepository.save(game);
                log.info("게임 [AppId: {}, 이름: {}] 메타데이터 보강 성공 (장르: {})", appId, game.getName(), genreNames);
            } else {
                log.debug("스팀 상점 API 응답 없음 [AppId: {}]", appId);
            }
        } catch (Exception e) {
            log.warn("게임 [AppId: {}] 메타데이터 보강 중 오류 발생: {}", appId, e.getMessage());
        }
    }
}
