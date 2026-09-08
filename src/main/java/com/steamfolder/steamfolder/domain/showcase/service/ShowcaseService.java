package com.steamfolder.steamfolder.domain.showcase.service;

import com.steamfolder.steamfolder.common.dto.CursorPageResponseDto;
import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.folder.dto.response.FolderResponse;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.repository.FolderRepository;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.showcase.dto.response.LikedShowcaseResponseDto;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseLikeResponse;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseRankingItemDto;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseRankingResponse;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseResponse;
import com.steamfolder.steamfolder.domain.showcase.entity.ShowcaseLike;
import com.steamfolder.steamfolder.domain.showcase.repository.ShowcaseLikeRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShowcaseService {

    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final UserGameRepository userGameRepository;
    private final ShowcaseLikeRepository showcaseLikeRepository;

    /**
     * 타 유저 쇼케이스 좋아요 토글 (누르면 좋아요 +1, 다시 누르면 취소 -1)
     * 동시성 방어:
     * 1. 동일 유저 더블 클릭 직렬화: currentUserId 비관적 락(PESSIMISTIC_WRITE)
     * 2. 다중 유저 동시 좋아요 카운트 분실(Lost Update) 방어: DB 원자적 increment/decrement 쿼리
     */
    @Transactional
    public ShowcaseLikeResponse toggleShowcaseLike(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.CANNOT_LIKE_OWN_SHOWCASE);
        }

        // 1. 현재 유저 행에 대해 비관적 락을 획득하여 동일 유저의 동시 요청 직렬화
        User user = userRepository.findByIdWithPessimisticLock(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.isDeleted()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        User targetUser = findUserById(targetUserId);

        Optional<ShowcaseLike> existingLike = showcaseLikeRepository.findByUserAndTargetUser(user, targetUser);

        boolean liked;
        if (existingLike.isPresent()) {
            showcaseLikeRepository.delete(existingLike.get());
            showcaseLikeRepository.flush();
            userRepository.decrementLikeCount(targetUserId);
            liked = false;
            log.info("쇼케이스 좋아요 취소: user={}, targetUser={}", currentUserId, targetUserId);
        } else {
            ShowcaseLike newLike = ShowcaseLike.builder()
                    .user(user)
                    .targetUser(targetUser)
                    .build();
            showcaseLikeRepository.saveAndFlush(newLike);
            userRepository.incrementLikeCount(targetUserId);
            liked = true;
            log.info("쇼케이스 좋아요 등록: user={}, targetUser={}", currentUserId, targetUserId);
        }

        int updatedLikeCount = userRepository.getLikeCount(targetUserId);
        return ShowcaseLikeResponse.of(targetUserId, liked, updatedLikeCount);
    }

    /**
     * 로그인한 내 쇼케이스 전체 뷰 조회
     */
    @Transactional(readOnly = true)
    public ShowcaseResponse getMyShowcase(Long userId) {
        User user = findUserById(userId);
        return buildShowcaseResponse(user);
    }

    /**
     * 게스트 및 타 유저를 위한 공개 쇼케이스 뷰 조회 (steamId 또는 username)
     */
    @Transactional(readOnly = true)
    public ShowcaseResponse getPublicShowcase(String identifier, Long requesterUserId) {
        User targetUser = findUserByIdentifier(identifier);

        boolean isLiked = false;
        if (requesterUserId != null) {
            isLiked = showcaseLikeRepository.existsByUser_IdAndTargetUser_Id(requesterUserId, targetUser.getId());
        }

        return buildShowcaseResponse(targetUser, isLiked);
    }

    private ShowcaseResponse buildShowcaseResponse(User user) {
        return buildShowcaseResponse(user, false);
    }

    private ShowcaseResponse buildShowcaseResponse(User user, boolean isLiked) {
        // 소유자의 UserGame 목록을 조회하여 appId 기준 매핑 (플레이타임 & 최근 플레이 일시 매핑)
        List<UserGame> userGames = userGameRepository.findByUser(user);
        Map<Long, UserGame> userGameMap = userGames.stream()
                .collect(Collectors.toMap(ug -> ug.getGame().getAppId(), ug -> ug, (a, b) -> a));

        // 1. 루트 폴더 목록 조회 (2D 격자 정렬)
        List<Folder> rootFolders = folderRepository.findByUserAndParentFolderIsNullOrderByPositionYAscPositionXAsc(user);
        List<FolderResponse> folderResponses = rootFolders.stream()
                .map(folder -> FolderResponse.from(folder, userGameMap))
                .toList();

        // 2. 쇼케이스 요약 통계 집계
        int totalFolders = (int) folderRepository.countByUser(user);
        int totalOwnedGames = (int) userGameRepository.countByUser(user);
        int totalPlaytime = userGameRepository.sumPlaytimeByUser(user);

        // 모든 폴더에 배치된 게임 수 계산
        int totalGamesInFolders = folderResponses.stream()
                .mapToInt(FolderResponse::getGameCount)
                .sum();

        ShowcaseResponse.ShowcaseStatsDto stats = ShowcaseResponse.ShowcaseStatsDto.builder()
                .totalFolders(totalFolders)
                .totalGamesInFolders(totalGamesInFolders)
                .totalOwnedGames(totalOwnedGames)
                .totalPlaytimeForever(totalPlaytime)
                .build();

        return ShowcaseResponse.builder()
                .user(ShowcaseResponse.ShowcaseUserDto.from(user, isLiked))
                .stats(stats)
                .rootFolders(folderResponses)
                .build();
    }

    private User findUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.isDeleted()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private User findUserByIdentifier(String identifier) {
        // steamId(숫자) 또는 username으로 검색
        User user = userRepository.findBySteamId(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * No-Offset 커서 기반 인기 쇼케이스 랭킹 조회
     * 정렬 기준: 1차 likeCount DESC, 2차 id ASC (Strict Total Order)
     * 대상: 공개(isPublic=true) 및 미탈퇴(deletedAt IS NULL) 유저
     * @param currentUserId 로그인 유저 ID (로그인 시 본인의 순위 및 좋아요 수를 myRanking으로 항상 반환)
     */
    @Transactional(readOnly = true)
    public ShowcaseRankingResponse getShowcaseRankings(Integer cursorLikeCount, Long cursorUserId, int limit, Long currentUserId) {
        if (limit < 1) {
            limit = 10;
        } else if (limit > 50) {
            limit = 50;
        }

        boolean hasCursor = (cursorLikeCount != null && cursorUserId != null);
        PageRequest pageRequest = PageRequest.of(0, limit + 1);

        List<User> users;
        long startRank;

        if (!hasCursor) {
            users = userRepository.findFirstPageShowcaseRankings(pageRequest);
            startRank = 1L;
        } else {
            users = userRepository.findNextPageShowcaseRankings(cursorLikeCount, cursorUserId, pageRequest);
            startRank = userRepository.countShowcasesAheadOfCursor(cursorLikeCount, cursorUserId) + 1L;
        }

        boolean hasNext = users.size() > limit;
        List<User> pagedUsers = hasNext ? users.subList(0, limit) : users;

        List<Long> userIds = pagedUsers.stream().map(User::getId).toList();
        Map<Long, Long> gameCounts = userIds.isEmpty() ? Map.of() :
                userGameRepository.countUserGamesByUserIds(userIds).stream()
                        .collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> (Long) row[1]
                        ));

        Set<Long> likedTargetUserIds = (currentUserId != null && !userIds.isEmpty())
                ? showcaseLikeRepository.findTargetUserIdsByUserIdAndTargetUserIdIn(currentUserId, userIds)
                : Set.of();

        List<ShowcaseRankingItemDto> content = new ArrayList<>();
        for (int i = 0; i < pagedUsers.size(); i++) {
            User u = pagedUsers.get(i);
            long rank = startRank + i;
            long totalGamesCount = gameCounts.getOrDefault(u.getId(), 0L);
            boolean isLiked = likedTargetUserIds.contains(u.getId());

            content.add(ShowcaseRankingItemDto.builder()
                    .rank(rank)
                    .userId(u.getId())
                    .username(u.getUsername())
                    .avatarUrl(u.getAvatarUrl())
                    .likeCount(u.getLikeCount())
                    .totalGamesCount(totalGamesCount)
                    .isLiked(isLiked)
                    .isPublic(true)
                    .build());
        }

        Integer nextCursorLikeCount = null;
        Long nextCursorUserId = null;
        if (hasNext && !pagedUsers.isEmpty()) {
            User lastUser = pagedUsers.get(pagedUsers.size() - 1);
            nextCursorLikeCount = lastUser.getLikeCount();
            nextCursorUserId = lastUser.getId();
        }

        // 로그인 사용자의 본인 순위 및 좋아요 수 계산 (첫 페이지 조회 시에만 1회 계산, 무한 스크롤 시 중복 쿼리 완전 방어)
        ShowcaseRankingItemDto myRanking = null;
        if (!hasCursor && currentUserId != null) {
            Optional<User> currentUserOpt = userRepository.findById(currentUserId);
            if (currentUserOpt.isPresent() && !currentUserOpt.get().isDeleted()) {
                User currentUser = currentUserOpt.get();
                Long myRank = currentUser.isPublic()
                        ? userRepository.countShowcasesStrictlyAhead(currentUser.getLikeCount(), currentUser.getId()) + 1L
                        : null;
                long myGamesCount = userGameRepository.countByUser(currentUser);

                myRanking = ShowcaseRankingItemDto.builder()
                        .rank(myRank)
                        .userId(currentUser.getId())
                        .username(currentUser.getUsername())
                        .avatarUrl(currentUser.getAvatarUrl())
                        .likeCount(currentUser.getLikeCount())
                        .totalGamesCount(myGamesCount)
                        .isLiked(false)
                        .isPublic(currentUser.isPublic())
                        .build();
            }
        }

        return ShowcaseRankingResponse.of(
                content,
                myRanking,
                nextCursorLikeCount,
                nextCursorUserId,
                hasNext,
                limit
        );
    }

    /**
     * 내가 좋아요를 누른 쇼케이스 목록 조회 (No-Offset 커서 페이징)
     * 대상: 탈퇴하지 않고(deletedAt IS NULL) 공개 상태(isPublic = true)인 유저의 쇼케이스만 노출
     * 정렬: 최근 좋아요 누른 순서 (ShowcaseLike id DESC)
     */
    @Transactional(readOnly = true)
    public CursorPageResponseDto<LikedShowcaseResponseDto> getMyLikedShowcases(Long userId, Long cursor, int size) {
        if (size < 1) {
            size = 20;
        } else if (size > 50) {
            size = 50;
        }

        User user = findUserById(userId);
        Pageable pageable = PageRequest.of(0, size + 1);

        List<ShowcaseLike> likes = (cursor == null)
                ? showcaseLikeRepository.findFirstPageLikedShowcases(user, pageable)
                : showcaseLikeRepository.findNextPageLikedShowcases(user, cursor, pageable);

        boolean hasNext = likes.size() > size;
        List<ShowcaseLike> pagedLikes = hasNext ? likes.subList(0, size) : likes;

        List<Long> targetUserIds = pagedLikes.stream()
                .map(sl -> sl.getTargetUser().getId())
                .toList();

        Map<Long, Long> gameCounts = targetUserIds.isEmpty() ? Map.of() :
                userGameRepository.countUserGamesByUserIds(targetUserIds).stream()
                        .collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> (Long) row[1]
                        ));

        List<LikedShowcaseResponseDto> content = pagedLikes.stream()
                .map(sl -> {
                    User target = sl.getTargetUser();
                    long gamesCount = gameCounts.getOrDefault(target.getId(), 0L);
                    return LikedShowcaseResponseDto.builder()
                            .likeId(sl.getId())
                            .userId(target.getId())
                            .steamId(target.getSteamId())
                            .username(target.getUsername())
                            .avatarUrl(target.getAvatarUrl())
                            .themeColor(target.getThemeColor())
                            .likeCount(target.getLikeCount())
                            .totalGamesCount(gamesCount)
                            .isLiked(true)
                            .likedAt(sl.getCreatedAt())
                            .build();
                })
                .toList();

        Long nextCursor = null;
        if (hasNext && !pagedLikes.isEmpty()) {
            nextCursor = pagedLikes.get(pagedLikes.size() - 1).getId();
        }

        long totalElements = showcaseLikeRepository.countActiveLikedShowcasesByUser(user);

        return CursorPageResponseDto.of(content, nextCursor, hasNext, totalElements, size);
    }
}
