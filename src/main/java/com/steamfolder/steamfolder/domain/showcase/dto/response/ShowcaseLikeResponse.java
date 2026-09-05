package com.steamfolder.steamfolder.domain.showcase.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowcaseLikeResponse {

    private Long targetUserId;
    private boolean liked;
    private int totalLikeCount;

    public static ShowcaseLikeResponse of(Long targetUserId, boolean liked, int totalLikeCount) {
        return ShowcaseLikeResponse.builder()
                .targetUserId(targetUserId)
                .liked(liked)
                .totalLikeCount(totalLikeCount)
                .build();
    }
}
