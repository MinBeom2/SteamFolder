package com.steamfolder.steamfolder.domain.game.controller;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.domain.game.dto.response.CooldownResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.SyncResultResponseDto;
import com.steamfolder.steamfolder.domain.game.dto.response.UserGameResponseDto;
import com.steamfolder.steamfolder.domain.game.service.SteamSyncService;
import com.steamfolder.steamfolder.domain.game.service.UserGameQueryService;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/steam")
@RequiredArgsConstructor
public class SteamController {

    private final SteamSyncService steamSyncService;
    private final UserGameQueryService userGameQueryService;
    private final UserRepository userRepository;

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<SyncResultResponseDto>> syncOwnedGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        User user = getUser(userPrincipal);
        SyncResultResponseDto response = steamSyncService.syncUserGames(user);
        return ResponseEntity.ok(ApiResponse.success("스팀 게임 라이브러리 동기화가 성공적으로 완료되었습니다.", response));
    }

    @GetMapping("/sync/cooldown")
    public ResponseEntity<ApiResponse<CooldownResponseDto>> getSyncCooldown(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        User user = getUser(userPrincipal);
        CooldownResponseDto response = steamSyncService.getCooldownStatus(user);
        return ResponseEntity.ok(ApiResponse.success("동기화 쿨다운 상태를 조회했습니다.", response));
    }

    @GetMapping("/user-games")
    public ResponseEntity<ApiResponse<List<UserGameResponseDto>>> getUserGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        User user = getUser(userPrincipal);
        List<UserGameResponseDto> response = userGameQueryService.getUserGames(user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private User getUser(UserPrincipal userPrincipal) {
        if (userPrincipal == null || userPrincipal.getUserId() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
