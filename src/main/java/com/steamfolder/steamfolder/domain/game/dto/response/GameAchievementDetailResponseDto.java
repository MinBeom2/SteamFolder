package com.steamfolder.steamfolder.domain.game.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameAchievementDetailResponseDto {

    private Long appId;
    private String gameName;
    private Integer totalAchievements;
    private Integer achievedAchievements;
    private Double achievementRate;
    private LocalDateTime syncedAt;
    private Integer playtimeForever;
    private LocalDateTime lastPlayedAt;
    private List<AchievementItemDto> achievements;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AchievementItemDto {
        private String apiName;
        private boolean achieved;
        private LocalDateTime unlockTime;
    }
}
