package com.steamfolder.steamfolder.domain.system.controller;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import com.steamfolder.steamfolder.common.ratelimit.RateLimitDecision;
import com.steamfolder.steamfolder.common.ratelimit.RateLimitFilter;
import com.steamfolder.steamfolder.domain.system.dto.response.RateLimitStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    @GetMapping("/rate-limit")
    public ResponseEntity<ApiResponse<RateLimitStatusResponse>> getRateLimitStatus(HttpServletRequest request) {
        Object attribute = request.getAttribute(RateLimitFilter.DECISION_ATTRIBUTE);
        if (!(attribute instanceof RateLimitDecision decision)) {
            return ResponseEntity.ok(ApiResponse.success("레이트 리밋이 비활성화되어 있습니다.", null));
        }
        return ResponseEntity.ok(ApiResponse.success(
                "현재 레이트 리밋 상태를 조회했습니다.",
                RateLimitStatusResponse.from(decision)
        ));
    }
}
