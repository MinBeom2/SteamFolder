package com.steamfolder.steamfolder.domain.user.controller;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.domain.user.dto.request.UpdateProfileRequest;
import com.steamfolder.steamfolder.domain.user.dto.request.UpdateVisibilityRequest;
import com.steamfolder.steamfolder.domain.user.dto.response.UserResponse;
import com.steamfolder.steamfolder.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        UserResponse response = userService.getMyProfile(userPrincipal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("내 프로필 조회가 완료되었습니다.", response));
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody @Valid UpdateProfileRequest request
    ) {
        UserResponse response = userService.updateProfile(userPrincipal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("프로필 설정이 성공적으로 수정되었습니다.", response));
    }

    @PatchMapping("/me/visibility")
    public ResponseEntity<ApiResponse<UserResponse>> updateVisibility(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody @Valid UpdateVisibilityRequest request
    ) {
        UserResponse response = userService.updateVisibility(userPrincipal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("쇼케이스 공개 범위가 성공적으로 수정되었습니다.", response));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        userService.deleteAccount(userPrincipal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴 및 연동 해제가 성공적으로 완료되었습니다.", null));
    }
}
