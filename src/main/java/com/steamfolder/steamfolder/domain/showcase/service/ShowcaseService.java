package com.steamfolder.steamfolder.domain.showcase.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.folder.dto.response.FolderResponse;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.repository.FolderRepository;
import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseResponse;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseLikeResponse;
import com.steamfolder.steamfolder.domain.showcase.entity.ShowcaseLike;
import com.steamfolder.steamfolder.domain.showcase.repository.ShowcaseLikeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

        // 비공개 쇼케이스 검증: 비공개 상태이고 본인이 아니면 접근 차단
        if (!targetUser.isPublic()) {
            if (requesterUserId == null || !requesterUserId.equals(targetUser.getId())) {
                throw new CustomException(ErrorCode.HANDLE_ACCESS_DENIED);
            }
        }

        return buildShowcaseResponse(targetUser);
    }

    private ShowcaseResponse buildShowcaseResponse(User user) {
        // 1. 루트 폴더 목록 조회 (2D 격자 정렬)
        List<Folder> rootFolders = folderRepository.findByUserAndParentFolderIsNullOrderByPositionYAscPositionXAsc(user);
        List<FolderResponse> folderResponses = rootFolders.stream()
                .map(FolderResponse::from)
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
                .user(ShowcaseResponse.ShowcaseUserDto.from(user))
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
}
