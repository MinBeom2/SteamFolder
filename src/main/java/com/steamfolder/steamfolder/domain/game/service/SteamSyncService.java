package com.steamfolder.steamfolder.domain.game.service;

import com.steamfolder.steamfolder.common.dto.CursorPageResponseDto;
import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.game.client.SteamApiClient;
import com.steamfolder.steamfolder.domain.game.dto.response.CooldownResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.FilteredGamesResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.GameAchievementDetailResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.SyncResultResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.UserGameResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamOwnedGamesResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamPlayerAchievementsResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamPlayerSummaryResponseDto;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import com.steamfolder.steamfolder.domain.game.repository.GameRepository;
import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.game.repository.specification.UserGameSpecification;
import com.steamfolder.steamfolder.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamSyncService {

    private final SteamApiClient steamApiClient;
    private final GameRepository gameRepository;
    private final UserGameRepository userGameRepository;
    private final SteamSyncCooldownManager cooldownManager;

    @Transactional
    public SyncResultResponseDto syncUserGames(User user) {
        // 1. 5분 쿨다운 유효성 검증
        cooldownManager.validateCooldown(user.getSteamId());

        // 2. 유저 프로필 및 게임 목록 공개 상태 검증
        Optional<SteamPlayerSummaryResponseDto.PlayerDto> summaryOpt = steamApiClient.getPlayerSummary(user.getSteamId());
        if (summaryOpt.isPresent() && !summaryOpt.get().isPublic()) {
            log.warn("User {} profile is private (communityvisibilitystate: {})",
                    user.getSteamId(), summaryOpt.get().getCommunityVisibilityState());
            throw new CustomException(ErrorCode.STEAM_PROFILE_PRIVATE);
        }

        // 3. 스팀 서버에서 보유 게임 목록 수신
        SteamOwnedGamesResponseDto.ResponseData ownedGamesData = steamApiClient.getUserOwnedGames(user.getSteamId());
        List<SteamOwnedGamesResponseDto.SteamGameDto> games = (ownedGamesData != null && ownedGamesData.getGames() != null)
                ? ownedGamesData.getGames()
                : Collections.emptyList();

        if (games.isEmpty() && summaryOpt.isPresent() && !summaryOpt.get().isPublic()) {
            throw new CustomException(ErrorCode.STEAM_PROFILE_PRIVATE);
        }

        int syncedCount = 0;
        int newlyAddedCount = 0;

        for (SteamOwnedGamesResponseDto.SteamGameDto steamGame : games) {
            Long appId = steamGame.getAppId();
            if (appId == null) continue;

            // Game 마스터 엔티티 생성 또는 조회
            Game game = gameRepository.findById(appId).orElseGet(() -> {
                String gameName = (steamGame.getName() != null && !steamGame.getName().isBlank())
                        ? steamGame.getName()
                        : "App " + appId;
                String headerImg = "https://cdn.akamai.steamstatic.com/steam/apps/" + appId + "/header.jpg";
                String heroImg = "https://cdn.akamai.steamstatic.com/steam/apps/" + appId + "/library_hero.jpg";

                Game newGame = Game.builder()
                        .appId(appId)
                        .name(gameName)
                        .headerImage(headerImg)
                        .heroImage(heroImg)
                        .genres(Collections.emptyList())
                        .developer("")
                        .lastSyncedAt(LocalDateTime.now())
                        .build();
                return gameRepository.save(newGame);
            });

            // UserGame 플레이타임 및 최근 플레이 일시 Upsert
            int playtime = steamGame.getPlaytimeForever();
            LocalDateTime lastPlayedAt = null;
            if (steamGame.getRtimeLastPlayed() != null && steamGame.getRtimeLastPlayed() > 0) {
                lastPlayedAt = LocalDateTime.ofInstant(Instant.ofEpochSecond(steamGame.getRtimeLastPlayed()), ZoneId.systemDefault());
            }

            Optional<UserGame> existingUserGame = userGameRepository.findByUserAndGame(user, game);
            if (existingUserGame.isPresent()) {
                UserGame ug = existingUserGame.get();
                ug.updatePlaytime(playtime, lastPlayedAt);
            } else {
                UserGame newUg = UserGame.builder()
                        .user(user)
                        .game(game)
                        .playtimeForever(playtime)
                        .lastPlayedAt(lastPlayedAt)
                        .build();
                userGameRepository.save(newUg);
                newlyAddedCount++;
            }
            syncedCount++;
        }

        // 4. 쿨다운 적용
        cooldownManager.applyCooldown(user.getSteamId());

        return SyncResultResponseDto.builder()
                .syncedGameCount(syncedCount)
                .newlyAddedCount(newlyAddedCount)
                .lastSyncedAt(LocalDateTime.now())
                .build();
    }

    @Transactional(readOnly = true)
    public CooldownResponseDto getCooldownStatus(User user) {
        long remaining = cooldownManager.getRemainingSeconds(user.getSteamId());
        LocalDateTime lastSyncedAt = cooldownManager.getLastSyncedAt(user.getSteamId());

        return CooldownResponseDto.builder()
                .isCooldownActive(remaining > 0)
                .remainingSeconds(remaining)
                .lastSyncedAt(lastSyncedAt)
                .build();
    }

    @Transactional(readOnly = true)
    public List<UserGameResponseDto> getUserGames(User user) {
        return userGameRepository.findByUserOrderByPlaytimeForeverDesc(user)
                .stream()
                .map(UserGameResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * No-Offset 커서 기반 유저 보유 게임 라이브러리 목록 페이징 조회
     */
    @Transactional(readOnly = true)
    public CursorPageResponseDto<UserGameResponseDto> getUserGamesWithCursor(User user, Long cursor, int size) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<UserGame> userGames;
        if (cursor == null) {
            userGames = userGameRepository.findByUserOrderByIdDesc(user, pageable);
        } else {
            userGames = userGameRepository.findByUserAndIdLessThanOrderByIdDesc(user, cursor, pageable);
        }

        boolean hasNext = userGames.size() > pageSize;
        List<UserGame> contentList = hasNext ? userGames.subList(0, pageSize) : userGames;

        Long nextCursor = null;
        if (hasNext && !contentList.isEmpty()) {
            nextCursor = contentList.get(contentList.size() - 1).getId();
        }

        long totalCount = userGameRepository.countByUser(user);

        List<UserGameResponseDto> content = contentList.stream()
                .map(UserGameResponseDto::from)
                .collect(Collectors.toList());

        return CursorPageResponseDto.of(content, nextCursor, hasNext, totalCount, pageSize);
    }

    /**
     * 유저 보유 게임 다중 조건(장르) 동적 필터링 및 정렬 (DB Repository 레벨 수행)
     */
    @Transactional(readOnly = true)
    public FilteredGamesResponseDto filterUserGames(
            User user,
            List<String> genres,
            String sort
    ) {
        String sortType = (sort != null) ? sort.toLowerCase().trim() : "playtime";
        Sort sortObj = switch (sortType) {
            case "name" -> Sort.by(Sort.Direction.ASC, "game.name");
            case "recent" -> Sort.by(Sort.Direction.DESC, "id");
            default -> Sort.by(Sort.Direction.DESC, "playtimeForever");
        };

        Specification<UserGame> spec = UserGameSpecification.withDynamicFilter(user, genres);
        List<UserGame> userGames = userGameRepository.findAll(spec, sortObj);

        List<UserGameResponseDto> filteredList = userGames.stream()
                .map(UserGameResponseDto::from)
                .collect(Collectors.toList());

        return FilteredGamesResponseDto.of(filteredList);
    }

    /**
     * 특정 게임 도전과제(업적) 온디맨드 실시간 동기화
     */
    @Transactional
    public GameAchievementDetailResponseDto syncUserGameAchievements(User user, Long appId) {
        UserGame userGame = userGameRepository.findByUserAndGame_AppId(user, appId)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_NOT_FOUND));

        Optional<SteamPlayerAchievementsResponseDto.PlayerStats> statsOpt =
                steamApiClient.getPlayerAchievements(user.getSteamId(), appId);

        List<GameAchievementDetailResponseDto.AchievementItemDto> items = new ArrayList<>();

        if (statsOpt.isPresent() && statsOpt.get().getAchievements() != null && !statsOpt.get().getAchievements().isEmpty()) {
            List<SteamPlayerAchievementsResponseDto.AchievementDto> achievements = statsOpt.get().getAchievements();
            int total = achievements.size();
            int achieved = (int) achievements.stream().filter(a -> a.getAchieved() == 1).count();
            double rate = (total > 0) ? Math.round(((double) achieved / total * 100.0) * 10.0) / 10.0 : 0.0;

            userGame.updateAchievements(total, achieved, rate);

            items = achievements.stream()
                    .map(a -> GameAchievementDetailResponseDto.AchievementItemDto.builder()
                            .apiName(a.getApiName())
                            .achieved(a.getAchieved() == 1)
                            .unlockTime(a.getUnlockTime() != null && a.getUnlockTime() > 0
                                    ? LocalDateTime.ofInstant(Instant.ofEpochSecond(a.getUnlockTime()), ZoneId.systemDefault())
                                    : null)
                            .build())
                    .toList();
        } else {
            // 도전과제가 없거나(0개) 비공개 프로필인 경우
            userGame.updateAchievements(0, 0, null);
        }

        return GameAchievementDetailResponseDto.builder()
                .appId(appId)
                .gameName(userGame.getGame().getName())
                .totalAchievements(userGame.getTotalAchievements())
                .achievedAchievements(userGame.getAchievedAchievements())
                .achievementRate(userGame.getAchievementRate())
                .syncedAt(userGame.getAchievementsSyncedAt())
                .achievements(items)
                .build();
    }

    /**
     * 특정 게임 도전과제 달성 현황 조회 (미동기화 시 자동 온디맨드 동기화)
     */
    @Transactional
    public GameAchievementDetailResponseDto getGameAchievements(User user, Long appId) {
        return syncUserGameAchievements(user, appId);
    }
}
