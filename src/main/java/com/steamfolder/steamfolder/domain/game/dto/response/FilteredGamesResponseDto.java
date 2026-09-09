package com.steamfolder.steamfolder.domain.game.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilteredGamesResponseDto {

    private int totalCount;
    private List<UserGameResponseDto> games;

    // 프론트엔드 CursorPageResponse 호환 필드 (기존 클라이언트 코드와의 무중단 하위 호환성 보장)
    private List<UserGameResponseDto> content;
    private Long nextCursor;
    private boolean hasNext;
    private long totalElements;
    private int pageSize;

    public static FilteredGamesResponseDto of(List<UserGameResponseDto> games) {
        return FilteredGamesResponseDto.builder()
                .totalCount(games.size())
                .games(games)
                .content(games)
                .nextCursor(null)
                .hasNext(false)
                .totalElements(games.size())
                .pageSize(games.size())
                .build();
    }
}
