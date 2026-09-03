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

    public static FilteredGamesResponseDto of(List<UserGameResponseDto> games) {
        return FilteredGamesResponseDto.builder()
                .totalCount(games.size())
                .games(games)
                .build();
    }
}
