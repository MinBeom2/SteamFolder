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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private SteamOpenIdValidator steamOpenIdValidator;

    @Mock
    private SteamApiClient steamApiClient;

    @Mock
    private SteamSyncService steamSyncService;

    @Test
    @DisplayName("신규 유저 스팀 OpenID 최초 로그인 시 자동 회원가입, 라이브러리 동기화 및 토큰 발급 검증")
    void loginWithSteam_NewUser_Success() {
        // given
        Map<String, String> openIdParams = Map.of("openid.claimed_id", "https://steamcommunity.com/openid/id/76561198000000001");
        SteamLoginRequest request = SteamLoginRequest.builder()
                .openIdParams(openIdParams)
                .build();

        String steamId = "76561198000000001";
        SteamPlayerSummaryResponseDto.PlayerDto playerDto = new SteamPlayerSummaryResponseDto.PlayerDto();
        ReflectionTestUtils.setField(playerDto, "personaName", "SteamGamer");
        ReflectionTestUtils.setField(playerDto, "avatarFull", "https://avatars.steamstatic.com/gamer.jpg");

        User mockUser = User.builder()
                .id(1L)
                .steamId(steamId)
                .username("SteamGamer")
                .avatarUrl("https://avatars.steamstatic.com/gamer.jpg")
                .themeColor("#1A1B26")
                .isPublic(true)
                .build();

        given(steamOpenIdValidator.validateAndExtractSteamId(openIdParams)).willReturn(steamId);
        given(steamApiClient.getPlayerSummary(steamId)).willReturn(Optional.of(playerDto));
        given(userRepository.findBySteamId(steamId)).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(mockUser);
        given(jwtTokenProvider.createAccessToken(1L, steamId, "SteamGamer")).willReturn("mock-access-token");
        given(jwtTokenProvider.createRefreshToken(1L, steamId, "SteamGamer")).willReturn("mock-refresh-token");
        given(refreshTokenRepository.findByUser(any(User.class))).willReturn(Optional.empty());

        // when
        AuthResponse response = authService.loginWithSteam(request);

        // then
        assertThat(response.isNewUser()).isTrue();
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("SteamGamer");
        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("mock-refresh-token");
        verify(userRepository).save(any(User.class));
        verify(steamSyncService).syncUserGames(mockUser); // 자동 동기화 호출 검증
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("탈퇴했던 유저가 다시 로그인 시 계정 복구(Reactivation), 라이브러리 동기화 및 재가입 처리")
    void loginWithSteam_SoftDeletedUser_Reactivates() {
        // given
        String steamId = "76561198000000009";
        SteamLoginRequest request = SteamLoginRequest.builder()
                .steamId(steamId)
                .username("ReturnedGamer")
                .avatarUrl("https://avatars.steamstatic.com/returned.jpg")
                .build();

        User softDeletedUser = User.builder()
                .id(9L)
                .steamId(steamId)
                .username("OldGamer")
                .deletedAt(LocalDateTime.now().minusDays(10))
                .build();

        given(userRepository.findBySteamId(steamId)).willReturn(Optional.of(softDeletedUser));
        given(jwtTokenProvider.createAccessToken(9L, steamId, "ReturnedGamer")).willReturn("reactivated-access-token");
        given(jwtTokenProvider.createRefreshToken(9L, steamId, "ReturnedGamer")).willReturn("reactivated-refresh-token");
        given(refreshTokenRepository.findByUser(softDeletedUser)).willReturn(Optional.empty());

        // when
        AuthResponse response = authService.loginWithSteam(request);

        // then
        assertThat(response.isNewUser()).isTrue(); // 재가입으로 플래그 설정
        assertThat(softDeletedUser.isDeleted()).isFalse();
        assertThat(softDeletedUser.getDeletedAt()).isNull();
        assertThat(softDeletedUser.getUsername()).isEqualTo("ReturnedGamer");
        assertThat(response.getAccessToken()).isEqualTo("reactivated-access-token");
        verify(steamSyncService).syncUserGames(softDeletedUser);
    }

    @Test
    @DisplayName("기존 유저 스팀 OpenID 로그인 시 프로필 업데이트 및 기존 계정 토큰 갱신")
    void loginWithSteam_ExistingUser_Success() {
        // given
        String steamId = "76561198000000002";
        SteamLoginRequest request = SteamLoginRequest.builder()
                .steamId(steamId)
                .username("UpdatedName")
                .avatarUrl("https://avatars.steamstatic.com/updated.jpg")
                .build();

        User existingUser = User.builder()
                .id(2L)
                .steamId(steamId)
                .username("OldName")
                .themeColor("#1A1B26")
                .isPublic(true)
                .build();

        RefreshToken existingToken = RefreshToken.builder()
                .id(10L)
                .user(existingUser)
                .token("old-refresh-token")
                .expiryDate(LocalDateTime.now().plusDays(3))
                .build();

        given(userRepository.findBySteamId(steamId)).willReturn(Optional.of(existingUser));
        given(jwtTokenProvider.createAccessToken(2L, steamId, "UpdatedName")).willReturn("new-access-token");
        given(jwtTokenProvider.createRefreshToken(2L, steamId, "UpdatedName")).willReturn("new-refresh-token");
        given(refreshTokenRepository.findByUser(existingUser)).willReturn(Optional.of(existingToken));

        // when
        AuthResponse response = authService.loginWithSteam(request);

        // then
        assertThat(response.isNewUser()).isFalse();
        assertThat(response.getUserId()).isEqualTo(2L);
        assertThat(response.getUsername()).isEqualTo("UpdatedName");
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(existingToken.getToken()).isEqualTo("new-refresh-token");
    }

    @Test
    @DisplayName("유효한 Refresh Token으로 새 Access/Refresh 토큰 재발급 성공")
    void refreshToken_Success() {
        // given
        TokenRefreshRequest request = TokenRefreshRequest.builder()
                .refreshToken("valid-refresh-token")
                .build();

        User mockUser = User.builder()
                .id(3L)
                .steamId("76561198000000003")
                .username("ActiveUser")
                .build();

        RefreshToken storedToken = RefreshToken.builder()
                .id(20L)
                .user(mockUser)
                .token("valid-refresh-token")
                .expiryDate(LocalDateTime.now().plusDays(5))
                .build();

        given(jwtTokenProvider.validateToken("valid-refresh-token")).willReturn(true);
        given(refreshTokenRepository.findByToken("valid-refresh-token")).willReturn(Optional.of(storedToken));
        given(jwtTokenProvider.createAccessToken(3L, "76561198000000003", "ActiveUser")).willReturn("brand-new-access-token");
        given(jwtTokenProvider.createRefreshToken(3L, "76561198000000003", "ActiveUser")).willReturn("brand-new-refresh-token");

        // when
        TokenRefreshResponse response = authService.refreshToken(request);

        // then
        assertThat(response.getAccessToken()).isEqualTo("brand-new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("brand-new-refresh-token");
        assertThat(storedToken.getToken()).isEqualTo("brand-new-refresh-token");
    }

    @Test
    @DisplayName("만료된 Refresh Token 요청 시 CustomException(EXPIRED_TOKEN) 발생 검증")
    void refreshToken_Expired_ThrowsException() {
        // given
        TokenRefreshRequest request = TokenRefreshRequest.builder()
                .refreshToken("expired-token")
                .build();

        User mockUser = User.builder().id(4L).steamId("76561198000000004").username("ExpiredUser").build();

        RefreshToken expiredStoredToken = RefreshToken.builder()
                .id(30L)
                .user(mockUser)
                .token("expired-token")
                .expiryDate(LocalDateTime.now().minusDays(1)) // 이미 만료됨
                .build();

        given(jwtTokenProvider.validateToken("expired-token")).willReturn(true);
        given(refreshTokenRepository.findByToken("expired-token")).willReturn(Optional.of(expiredStoredToken));

        // when & then
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPIRED_TOKEN);

        verify(refreshTokenRepository).delete(expiredStoredToken);
    }
}
