package com.steamfolder.steamfolder.domain.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LikedShowcaseResponseDto {

    private Long likeId;
    private Long userId;
    private String steamId;
    private String username;
    private String avatarUrl;
    private String themeColor;
    private int likeCount;
    private long totalGamesCount;

    @JsonProperty("isLiked")
    private boolean isLiked;

    private LocalDateTime likedAt;

    @JsonProperty("isLiked")
    public boolean isLiked() {
        return isLiked;
    }
}
