package com.steamfolder.steamfolder.domain.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowcaseRankingItemDto {

    private Long rank;
    private Long userId;
    private String username;
    private String avatarUrl;
    private int likeCount;
    private long totalGamesCount;
    private long totalPlaytimeForever;

    @JsonProperty("isLiked")
    private boolean isLiked;

    @JsonProperty("isPublic")
    private boolean isPublic;

    @JsonProperty("isLiked")
    public boolean isLiked() {
        return isLiked;
    }

    @JsonProperty("isPublic")
    public boolean isPublic() {
        return isPublic;
    }
}
