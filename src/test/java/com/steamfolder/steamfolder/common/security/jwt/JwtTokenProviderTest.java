package com.steamfolder.steamfolder.common.security.jwt;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    // 테스트 환경 격리용 더미 비밀키 (실제 비밀키와 완전히 다름)
    private final String testDummySecretKey = "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RpbmctcHVycG9zZXMtb25seS1wbGVhc2UtZG8tbm90LXVzZS1pbi1wcm9kdWN0aW9uLTIwMjYwODI0";

    @BeforeEach
    void setUp() {
        // Access token: 3600s, Refresh token: 604800s
        tokenProvider = new JwtTokenProvider(testDummySecretKey, 3600, 604800);
    }

    @Test
    @DisplayName("Access Token 생성 및 클레임(userId, steamId, username) 파싱 검증")
    void createAndParseAccessToken() {
        // given
        Long userId = 1L;
        String steamId = "76561198012345678";
        String username = "SteamMaster";

        // when
        String token = tokenProvider.createAccessToken(userId, steamId, username);

        // then
        assertThat(token).isNotBlank();
        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getUserId(token)).isEqualTo(userId);
        assertThat(tokenProvider.getSteamId(token)).isEqualTo(steamId);

        Authentication authentication = tokenProvider.getAuthentication(token);
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(UserPrincipal.class);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(userId);
        assertThat(principal.getSteamId()).isEqualTo(steamId);
        assertThat(principal.getUsername()).isEqualTo(username);
        assertThat(principal.getAuthorities()).extracting("authority").contains("ROLE_USER");
    }

    @Test
    @DisplayName("만료 시간이 지난 토큰 검증 시 CustomException(EXPIRED_TOKEN) 발생 검증")
    void validateExpiredToken() {
        // given: 유효기간이 -1초인 만료 토큰 생성기
        JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(testDummySecretKey, -1, -1);
        String expiredToken = expiredTokenProvider.createAccessToken(1L, "76561198000000000", "ExpiredUser");

        // when & then
        assertThatThrownBy(() -> tokenProvider.validateToken(expiredToken))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("만료된 토큰입니다.");
    }

    @Test
    @DisplayName("위조되거나 잘못된 형식의 토큰 검증 시 CustomException(INVALID_TOKEN) 발생 검증")
    void validateInvalidToken() {
        // given
        String malformedToken = "invalid.bearer.token.string";

        // when & then
        assertThatThrownBy(() -> tokenProvider.validateToken(malformedToken))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("유효하지 않은 토큰입니다.");
    }
}
