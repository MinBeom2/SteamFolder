package com.steamfolder.steamfolder.domain.game.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.repository.GameRepository;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameSeedDataLoader implements ApplicationRunner {

    private final GameRepository gameRepository;
    private final ObjectMapper objectMapper;

    private static final String SEED_DATA_PATH = "data/steam_popular_games_seed.json";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        loadSeedData();
    }

    public void loadSeedData() {
        try {
            ClassPathResource resource = new ClassPathResource(SEED_DATA_PATH);
            if (!resource.exists()) {
                log.warn("스팀 인기 게임 시드 파일({})을 찾을 수 없습니다. 시드 적재를 건너뜁니다.", SEED_DATA_PATH);
                return;
            }

            List<SeedGameDto> seedGames;
            try (InputStream is = resource.getInputStream()) {
                seedGames = objectMapper.readValue(is, new TypeReference<List<SeedGameDto>>() {});
            }

            if (seedGames == null || seedGames.isEmpty()) {
                log.info("스팀 시드 게임 데이터가 비어 있습니다.");
                return;
            }

            List<Long> appIds = seedGames.stream().map(SeedGameDto::getAppId).toList();
            Map<Long, Game> existingGamesMap = gameRepository.findAllById(appIds).stream()
                    .collect(Collectors.toMap(Game::getAppId, g -> g));

            List<Game> toSave = new ArrayList<>();
            int inserted = 0;
            int updated = 0;

            for (SeedGameDto seed : seedGames) {
                if (seed.getAppId() == null) {
                    continue;
                }

                Game existing = existingGamesMap.get(seed.getAppId());
                if (existing == null) {
                    Game newGame = Game.builder()
                            .appId(seed.getAppId())
                            .name(seed.getName() != null ? seed.getName() : "App " + seed.getAppId())
                            .headerImage(seed.getHeaderImage())
                            .heroImage(seed.getHeroImage())
                            .genres(seed.getGenres() != null ? seed.getGenres() : new ArrayList<>())
                            .developer(seed.getDeveloper() != null ? seed.getDeveloper() : "")
                            .releaseDate(seed.getReleaseDate() != null ? seed.getReleaseDate() : "")
                            .lastSyncedAt(LocalDateTime.now())
                            .build();
                    toSave.add(newGame);
                    inserted++;
                } else {
                    boolean needGenreUpdate = existing.getGenres() == null || existing.getGenres().isEmpty();
                    boolean needImageUpdate = existing.getHeroImage() == null || existing.getHeroImage().isBlank();
                    if (needGenreUpdate || needImageUpdate) {
                        existing.updateMetadata(
                                seed.getName(),
                                seed.getHeaderImage(),
                                seed.getHeroImage(),
                                (seed.getGenres() != null && !seed.getGenres().isEmpty()) ? seed.getGenres() : existing.getGenres(),
                                seed.getDeveloper(),
                                seed.getReleaseDate()
                        );
                        toSave.add(existing);
                        updated++;
                    }
                }
            }

            if (!toSave.isEmpty()) {
                gameRepository.saveAll(toSave);
            }

            log.info("인기 스팀 게임 시드 데이터 적재 완료: 신규 등록 {}개, 메타데이터 보강 {}개 (총 {}개 시드 대상)",
                    inserted, updated, seedGames.size());

        } catch (Exception e) {
            log.error("스팀 인기 게임 시드 데이터 적재 중 오류가 발생했습니다.", e);
        }
    }

    @Getter
    @NoArgsConstructor
    public static class SeedGameDto {
        private Long appId;
        private String name;
        private String headerImage;
        private String heroImage;
        private List<String> genres;
        private String developer;
        private String releaseDate;
    }
}
