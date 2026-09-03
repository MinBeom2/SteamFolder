package com.steamfolder.steamfolder.domain.game.dto.steam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SteamPlayerAchievementsResponseDto {

    @JsonProperty("playerstats")
    private PlayerStats playerStats;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerStats {
        @JsonProperty("steamID")
        private String steamId;

        @JsonProperty("gameName")
        private String gameName;

        @JsonProperty("achievements")
        private List<AchievementDto> achievements = new ArrayList<>();

        @JsonProperty("success")
        private boolean success;

        @JsonProperty("error")
        private String error;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AchievementDto {
        @JsonProperty("apiname")
        private String apiName;

        @JsonProperty("achieved")
        private int achieved; // 1 = 달성, 0 = 미달성

        @JsonProperty("unlocktime")
        private Long unlockTime;
    }
}
