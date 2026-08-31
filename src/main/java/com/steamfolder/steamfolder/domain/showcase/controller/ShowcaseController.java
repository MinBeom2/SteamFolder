package com.steamfolder.steamfolder.domain.showcase.controller;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseResponse;
import com.steamfolder.steamfolder.domain.showcase.service.ShowcaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/showcases")
@RequiredArgsConstructor
public class ShowcaseController {

    private final ShowcaseService showcaseService;

    /**
     * 로그인한 본인의 전체 쇼케이스 뷰 조회 API
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ShowcaseResponse>> getMyShowcase(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ShowcaseResponse response = showcaseService.getMyShowcase(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("내 쇼케이스를 성공적으로 조회했습니다.", response));
    }

    /**
     * 게스트 및 타 유저의 공개 쇼케이스 뷰 조회 API (steamId 또는 username)
     */
    @GetMapping("/{identifier}")
    public ResponseEntity<ApiResponse<ShowcaseResponse>> getPublicShowcase(
            @PathVariable String identifier,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long requesterUserId = principal != null ? principal.getUserId() : null;
        ShowcaseResponse response = showcaseService.getPublicShowcase(identifier, requesterUserId);
        return ResponseEntity.ok(ApiResponse.success("공개 쇼케이스를 성공적으로 조회했습니다.", response));
    }
}
