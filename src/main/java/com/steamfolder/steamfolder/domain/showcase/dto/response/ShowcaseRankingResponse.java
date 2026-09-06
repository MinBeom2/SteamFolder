package com.steamfolder.steamfolder.domain.showcase.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowcaseRankingResponse {

    private List<ShowcaseRankingItemDto> content;
    private ShowcaseRankingItemDto myRanking;
    private Integer nextCursorLikeCount;
    private Long nextCursorUserId;
    private boolean hasNext;
    private int pageSize;

    public static ShowcaseRankingResponse of(
            List<ShowcaseRankingItemDto> content,
            ShowcaseRankingItemDto myRanking,
            Integer nextCursorLikeCount,
            Long nextCursorUserId,
            boolean hasNext,
            int pageSize
    ) {
        return ShowcaseRankingResponse.builder()
                .content(content)
                .myRanking(myRanking)
                .nextCursorLikeCount(nextCursorLikeCount)
                .nextCursorUserId(nextCursorUserId)
                .hasNext(hasNext)
                .pageSize(pageSize)
                .build();
    }
}
