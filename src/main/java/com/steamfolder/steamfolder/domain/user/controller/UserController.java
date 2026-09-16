package com.steamfolder.steamfolder.domain.user.controller;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.common.dto.CursorPageResponseDto;
import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.game.dto.response.FilteredGamesResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.UserGameResponseDto;
import com.steamfolder.steamfolder.domain.game.service.SteamSyncService;
import com.steamfolder.steamfolder.domain.game.service.UserGameQueryService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SteamSyncService steamSyncService;
    private final UserGameQueryService userGameQueryService;
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
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        CursorPageResponseDto<UserGameResponseDto> response = userGameQueryService.getUserGamesWithCursor(user, cursor, size);
        return ResponseEntity.ok(ApiResponse.success("내 게임 라이브러리 목록을 성공적으로 조회했습니다.", response));
    }

    /**
     * 보유 게임 다중 조건 동적 필터링 API
     */
    @GetMapping("/me/games/filter")
    public ResponseEntity<ApiResponse<FilteredGamesResponseDto>> filterMyGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) List<String> genres,
            @RequestParam(required = false, defaultValue = "playtime") String sort
    ) {
        User user = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        FilteredGamesResponseDto response = userGameQueryService.filterUserGames(user, genres, sort);
        return ResponseEntity.ok(ApiResponse.success("게임 동적 필터링 조회가 완료되었습니다.", response));
    }

    /**
     * 특정 게임 도전과제(업적) 온디맨드 실시간 동기화 API
     */
    @PostMapping("/me/games/{appId}/achievements/sync")
    public ResponseEntity<ApiResponse<com.steamfolder.steamfolder.domain.game.dto.response.GameAchievementDetailResponseDto>> syncAchievements(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long appId,
            @RequestParam(required = false, defaultValue = "false") boolean force
    ) {
        User user = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        com.steamfolder.steamfolder.domain.game.dto.response.GameAchievementDetailResponseDto response =
                steamSyncService.syncUserGameAchievements(user, appId, force);
        return ResponseEntity.ok(ApiResponse.success("게임 도전과제(업적) 동기화가 완료되었습니다.", response));
    }

    /**
     * 특정 게임 도전과제(업적) 달성 현황 및 세부 목록 조회 API
     */
    @GetMapping("/me/games/{appId}/achievements")
    public ResponseEntity<ApiResponse<com.steamfolder.steamfolder.domain.game.dto.response.GameAchievementDetailResponseDto>> getAchievements(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long appId
    ) {
        User user = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        com.steamfolder.steamfolder.domain.game.dto.response.GameAchievementDetailResponseDto response =
                steamSyncService.getGameAchievements(user, appId);
        return ResponseEntity.ok(ApiResponse.success("게임 도전과제(업적) 조회가 완료되었습니다.", response));
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

    @DeleteMapping("/me/workspace")
    public ResponseEntity<ApiResponse<Void>> resetWorkspace(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        userService.resetWorkspace(userPrincipal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("모든 폴더와 폴더에 등록된 게임이 초기화되었습니다.", null));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        userService.deleteAccount(userPrincipal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴 및 사용자 데이터 삭제가 완료되었습니다.", null));
    }
}
