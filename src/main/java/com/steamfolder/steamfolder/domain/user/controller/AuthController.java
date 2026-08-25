package com.steamfolder.steamfolder.domain.user.controller;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import com.steamfolder.steamfolder.domain.user.dto.request.SteamLoginRequest;
import com.steamfolder.steamfolder.domain.user.dto.request.TokenRefreshRequest;
import com.steamfolder.steamfolder.domain.user.dto.response.AuthResponse;
import com.steamfolder.steamfolder.domain.user.dto.response.TokenRefreshResponse;
import com.steamfolder.steamfolder.domain.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/steam/login")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithSteam(@RequestBody @Valid SteamLoginRequest request) {
        AuthResponse response = authService.loginWithSteam(request);
        HttpStatus status = response.isNewUser() ? HttpStatus.CREATED : HttpStatus.OK;
        String message = response.isNewUser() ? "회원가입 및 로그인이 완료되었습니다." : "로그인이 완료되었습니다.";
        return ResponseEntity.status(status).body(ApiResponse.success(message, response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(@RequestBody @Valid TokenRefreshRequest request) {
        TokenRefreshResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Access Token이 성공적으로 재발급되었습니다.", response));
    }
}
