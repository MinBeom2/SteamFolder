package com.steamfolder.steamfolder.domain.game.dto.response;

import com.steamfolder.steamfolder.domain.game.entity.UserGame;
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
public class UserGameResponseDto {
    private Long userGameId;
    private Long appId;
    private String name;
    private int playtimeForever;
    private LocalDateTime lastPlayedAt;
    private String headerImage;
    private String heroImage;
    private List<String> genres;
    private String developer;
    private Integer totalAchievements;
    private Integer achievedAchievements;
    private Double achievementRate;

    public static UserGameResponseDto from(UserGame userGame) {
        return UserGameResponseDto.builder()
                .userGameId(userGame.getId())
                .appId(userGame.getGame().getAppId())
                .name(userGame.getGame().getName())
                .playtimeForever(userGame.getPlaytimeForever())
                .lastPlayedAt(userGame.getLastPlayedAt())
                .headerImage(userGame.getGame().getHeaderImage())
                .heroImage(userGame.getGame().getHeroImage())
                .genres(userGame.getGame().getGenres())
                .developer(userGame.getGame().getDeveloper())
                .totalAchievements(userGame.getTotalAchievements())
                .achievedAchievements(userGame.getAchievedAchievements())
                .achievementRate(userGame.getAchievementRate())
                .build();
    }
}
