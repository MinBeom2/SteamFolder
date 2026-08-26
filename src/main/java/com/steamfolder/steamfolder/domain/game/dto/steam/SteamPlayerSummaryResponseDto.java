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
public class SteamPlayerSummaryResponseDto {

    @JsonProperty("response")
    private ResponseData response;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseData {
        @JsonProperty("players")
        private List<PlayerDto> players = new ArrayList<>();
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerDto {
        @JsonProperty("steamid")
        private String steamId;

        @JsonProperty("personaname")
        private String personaName;

        @JsonProperty("avatarfull")
        private String avatarFull;

        /**
         * 1: Private (비공개)
         * 2: Friends Only (친구만)
         * 3: Public (전체 공개)
         */
        @JsonProperty("communityvisibilitystate")
        private int communityVisibilityState;

        public boolean isPublic() {
            return this.communityVisibilityState == 3;
        }
    }
}
