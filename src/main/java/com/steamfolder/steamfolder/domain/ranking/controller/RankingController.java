package com.steamfolder.steamfolder.domain.ranking.controller;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseRankingResponse;
import com.steamfolder.steamfolder.domain.showcase.service.ShowcaseService;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final ShowcaseService showcaseService;

    /**
     * No-Offset 커서 기반 인기 쇼케이스 랭킹 조회 API
     * 게스트 및 비로그인 사용자 열람 가능
     * 로그인 사용자의 경우 본인의 순위 및 좋아요 개수(myRanking)를 항상 함께 반환
     */
    @GetMapping("/showcases")
    public ResponseEntity<ApiResponse<ShowcaseRankingResponse>> getShowcaseRankings(
            @RequestParam(required = false) Integer cursorLikeCount,
            @RequestParam(required = false) Long cursorUserId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long currentUserId = principal != null ? principal.getUserId() : null;
        ShowcaseRankingResponse response = showcaseService.getShowcaseRankings(cursorLikeCount, cursorUserId, limit, currentUserId);
        return ResponseEntity.ok(ApiResponse.success("인기 쇼케이스 랭킹 조회 성공", response));
    }
}
