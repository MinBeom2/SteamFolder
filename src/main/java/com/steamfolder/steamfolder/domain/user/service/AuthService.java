package com.steamfolder.steamfolder.domain.user.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.common.security.jwt.JwtTokenProvider;
import com.steamfolder.steamfolder.domain.game.client.SteamApiClient;
import com.steamfolder.steamfolder.domain.game.dto.steam.SteamPlayerSummaryResponseDto;
import com.steamfolder.steamfolder.domain.game.service.SteamSyncService;
import com.steamfolder.steamfolder.domain.user.dto.request.SteamLoginRequest;
import com.steamfolder.steamfolder.domain.user.dto.request.TokenRefreshRequest;
import com.steamfolder.steamfolder.domain.user.dto.response.AuthResponse;
import com.steamfolder.steamfolder.domain.user.dto.response.TokenRefreshResponse;
import com.steamfolder.steamfolder.domain.user.entity.RefreshToken;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.RefreshTokenRepository;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final SteamOpenIdValidator steamOpenIdValidator;
    private final SteamApiClient steamApiClient;
    private final SteamSyncService steamSyncService;
    private final UserDataCleanupService userDataCleanupService;

    @Transactional
    public AuthResponse loginWithSteam(SteamLoginRequest request) {
        String steamId;

        // 1. OpenID 검증 또는 직접 전달된 steamId 추출
        if (request.getOpenIdParams() != null && !request.getOpenIdParams().isEmpty()) {
            steamId = steamOpenIdValidator.validateAndExtractSteamId(request.getOpenIdParams());
        } else if (request.getSteamId() != null && !request.getSteamId().isBlank()) {
            steamId = request.getSteamId();
        } else {
            throw new CustomException(ErrorCode.STEAM_AUTH_FAILED, "스팀 인증 정보가 올바르지 않습니다.");
        }

        // 2. 스팀 공식 API를 통해 실제 닉네임과 아바타 이미지 조회
        String realUsername = request.getUsername();
        String realAvatarUrl = request.getAvatarUrl();

        try {
            Optional<SteamPlayerSummaryResponseDto.PlayerDto> playerSummary = steamApiClient.getPlayerSummary(steamId);
            if (playerSummary.isPresent()) {
                SteamPlayerSummaryResponseDto.PlayerDto player = playerSummary.get();
                if (player.getPersonaName() != null && !player.getPersonaName().isBlank()) {
                    realUsername = player.getPersonaName();
                }
                if (player.getAvatarFull() != null && !player.getAvatarFull().isBlank()) {
                    realAvatarUrl = player.getAvatarFull();
                }
            }
        } catch (Exception e) {
            log.warn("스팀 프로필 정보 조회 실패 (기본값 사용): steamId={}", steamId, e);
        }

        if (realUsername == null || realUsername.isBlank()) {
            realUsername = "SteamUser_" + (steamId.length() > 4 ? steamId.substring(steamId.length() - 4) : steamId);
        }

        // 3. 유저 조회 또는 신규 회원가입 / 탈퇴 계정 재가입(Reactivation)
        Optional<User> optionalUser = userRepository.findBySteamId(steamId);
        boolean isNewUser;
        User user;

        if (optionalUser.isEmpty()) {
            isNewUser = true;
            user = userRepository.save(User.builder()
                    .steamId(steamId)
                    .username(realUsername)
                    .avatarUrl(realAvatarUrl)
                    .themeColor("#1A1B26")
                    .isPublic(true)
                    .build());
            log.info("신규 유저 회원가입 완료: userId={}, steamId={}, username={}", user.getId(), user.getSteamId(), user.getUsername());
        } else {
            user = optionalUser.get();
            if (user.isDeleted()) {
                isNewUser = true;
                userDataCleanupService.purgeAccountData(user);
                user.reactivate(realUsername, realAvatarUrl);
                log.info("탈퇴 유저 신규 상태 재가입 완료: userId={}, steamId={}, username={}", user.getId(), user.getSteamId(), user.getUsername());
            } else {
                isNewUser = false;
                user.syncSteamProfile(realUsername, realAvatarUrl);
                log.info("기존 유저 로그인 및 프로필 동기화 완료: userId={}, steamId={}, username={}", user.getId(), user.getSteamId(), user.getUsername());
            }
        }

        // 4. 신규 가입 또는 탈퇴 후 재가입 시 최초 게임 라이브러리 자동 동기화 시도 (실패해도 로그인 성공)
        if (isNewUser) {
            try {
                steamSyncService.syncUserGames(user);
                log.info("신규 가입/재가입 유저 게임 라이브러리 최초 자동 동기화 완료: userId={}", user.getId());
            } catch (Exception e) {
                log.warn("신규 가입 유저 최초 자동 동기화 건너뜀 (API Key 누락 또는 비공개 프로필 등): userId={}, error={}", user.getId(), e.getMessage());
            }
        }

        // 5. JWT 토큰 발급
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getSteamId(), user.getUsername());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getSteamId(), user.getUsername());

        // 6. RefreshToken DB 적재 및 갱신 (7일 만료)
        LocalDateTime expiryDate = LocalDateTime.now().plusDays(7);
        refreshTokenRepository.findByUser(user)
                .ifPresentOrElse(
                        token -> token.updateToken(refreshToken, expiryDate),
                        () -> refreshTokenRepository.save(RefreshToken.builder()
                                .user(user)
                                .token(refreshToken)
                                .expiryDate(expiryDate)
                                .build())
                );

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .steamId(user.getSteamId())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .themeColor(user.getThemeColor())
                .isNewUser(isNewUser)
                .build();
    }

    @Transactional
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        // 1. JWT 서명 및 유효성 검증
        jwtTokenProvider.validateToken(requestRefreshToken);

        // 2. DB에 저장된 RefreshToken 조회
        RefreshToken storedToken = refreshTokenRepository.findByToken(requestRefreshToken)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN, "존재하지 않거나 만료된 Refresh Token입니다."));

        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new CustomException(ErrorCode.EXPIRED_TOKEN, "만료된 Refresh Token입니다. 다시 로그인해 주세요.");
        }

        User user = storedToken.getUser();

        // 3. 새로운 Access Token 및 Refresh Token 발급 (RTR)
        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getSteamId(), user.getUsername());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getSteamId(), user.getUsername());

        storedToken.updateToken(newRefreshToken, LocalDateTime.now().plusDays(7));

        log.info("JWT Access Token 재발급 완료: userId={}", user.getId());

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }
}
