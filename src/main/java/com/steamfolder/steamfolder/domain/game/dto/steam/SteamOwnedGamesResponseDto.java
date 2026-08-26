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
public class SteamOwnedGamesResponseDto {

    @JsonProperty("response")
    private ResponseData response;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseData {
        @JsonProperty("game_count")
        private int gameCount;

        @JsonProperty("games")
        private List<SteamGameDto> games = new ArrayList<>();
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SteamGameDto {
        @JsonProperty("appid")
        private Long appId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("playtime_forever")
        private int playtimeForever;

        @JsonProperty("img_icon_url")
        private String imgIconUrl;

        @JsonProperty("rtime_last_played")
        private Long rtimeLastPlayed;
    }
}
