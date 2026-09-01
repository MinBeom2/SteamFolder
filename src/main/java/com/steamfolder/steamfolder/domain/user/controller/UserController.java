package com.steamfolder.steamfolder.domain.user.controller;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.common.dto.CursorPageResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.UserGameResponseDto;
import com.steamfolder.steamfolder.domain.game.service.SteamSyncService;
import com.steamfolder.steamfolder.domain.user.dto.request.UpdateProfileRequest;
import com.steamfolder.steamfolder.domain.user.dto.request.UpdateVisibilityRequest;
import com.steamfolder.steamfolder.domain.user.dto.response.UserResponse;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
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
    private final SteamSyncService steamSyncService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        UserResponse response = userService.getMyProfile(userPrincipal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("내 프로필 조회가 완료되었습니다.", response));
    }

    /**
     * No-Offset 커서 기반 내 게임 라이브러리 목록 페이징 조회 API
     */
    @GetMapping("/me/games")
    public ResponseEntity<ApiResponse<CursorPageResponseDto<UserGameResponseDto>>> getMyGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        User user = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new com.steamfolder.steamfolder.common.exception.CustomException(
                        com.steamfolder.steamfolder.common.exception.ErrorCode.USER_NOT_FOUND));

        CursorPageResponseDto<UserGameResponseDto> response = steamSyncService.getUserGamesWithCursor(user, cursor, size);
        return ResponseEntity.ok(ApiResponse.success("내 게임 라이브러리 목록을 성공적으로 조회했습니다.", response));
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
