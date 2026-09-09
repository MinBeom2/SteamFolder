package com.steamfolder.steamfolder.domain.game.service;

import com.steamfolder.steamfolder.common.dto.CursorPageResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.FilteredGamesResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.UserGameResponseDto;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
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

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserGameQueryService {

    private final UserGameRepository userGameRepository;

    /**
     * 유저 보유 게임 전체 목록 조회 (플레이타임 내림차순)
     */
    public List<UserGameResponseDto> getUserGames(User user) {
        return userGameRepository.findByUserOrderByPlaytimeForeverDesc(user)
                .stream()
                .map(UserGameResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * No-Offset 커서 기반 유저 보유 게임 라이브러리 목록 페이징 조회
     */
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
     * 유저 보유 게임 다중 조건(장르) 동적 필터링 및 DB 레벨 정렬 조회
     */
    public FilteredGamesResponseDto filterUserGames(User user, List<String> genres, String sort) {
        String sortType = (sort != null) ? sort.toLowerCase().trim() : "playtime";
        Sort sortObj = switch (sortType) {
            case "name", "name-asc", "name_asc" -> Sort.by(Sort.Order.asc("game.name"), Sort.Order.desc("id"));
            case "name-desc", "name_desc" -> Sort.by(Sort.Order.desc("game.name"), Sort.Order.desc("id"));
            case "recent", "recent-play", "recent_play", "lastplayed", "last_played" ->
                    Sort.by(Sort.Order.desc("lastPlayedAt"), Sort.Order.desc("playtimeForever"), Sort.Order.desc("id"));
            default -> Sort.by(Sort.Order.desc("playtimeForever"), Sort.Order.desc("id"));
        };

        Specification<UserGame> spec = UserGameSpecification.withDynamicFilter(user, genres);
        List<UserGame> userGames = userGameRepository.findAll(spec, sortObj);

        List<UserGameResponseDto> filteredList = userGames.stream()
                .map(UserGameResponseDto::from)
                .collect(Collectors.toList());

        return FilteredGamesResponseDto.of(filteredList);
    }
}
