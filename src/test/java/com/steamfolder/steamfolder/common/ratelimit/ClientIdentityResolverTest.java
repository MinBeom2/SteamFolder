package com.steamfolder.steamfolder.common.ratelimit;

import com.steamfolder.steamfolder.common.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIdentityResolverTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 사용자는 IP 대신 사용자 ID를 버킷 키로 사용한다")
    void resolvesAuthenticatedUser() {
        ClientIdentityResolver resolver = new ClientIdentityResolver(new RateLimitProperties());
        UserPrincipal principal = UserPrincipal.builder().userId(42L).username("tester").steamId("steam-42").build();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(principal, null);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        RateLimitIdentity identity = resolver.resolve(new MockHttpServletRequest());

        assertThat(identity.type()).isEqualTo(RateLimitIdentityType.USER);
        assertThat(identity.value()).isEqualTo("42");
    }

    @Test
    @DisplayName("인증 API는 로그인된 요청이어도 IP를 버킷 키로 사용한다")
    void resolvesAuthenticationApiByIp() {
        ClientIdentityResolver resolver = new ClientIdentityResolver(new RateLimitProperties());
        UserPrincipal principal = UserPrincipal.builder().userId(42L).username("tester").steamId("steam-42").build();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(principal, null);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.20");

        RateLimitIdentity identity = resolver.resolve(request, RateLimitPolicy.AUTH);

        assertThat(identity.type()).isEqualTo(RateLimitIdentityType.IP);
        assertThat(identity.value()).isEqualTo("203.0.113.20");
    }

    @Test
    @DisplayName("신뢰 프록시가 꺼진 환경에서는 X-Forwarded-For를 무시한다")
    void ignoresForwardedForWhenProxyIsNotTrusted() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustedProxyEnabled(false);
        ClientIdentityResolver resolver = new ClientIdentityResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.20");
        request.addHeader("X-Forwarded-For", "198.51.100.10");

        RateLimitIdentity identity = resolver.resolve(request);

        assertThat(identity.type()).isEqualTo(RateLimitIdentityType.IP);
        assertThat(identity.value()).isEqualTo("203.0.113.20");
    }

    @Test
    @DisplayName("신뢰 프록시 환경에서는 X-Forwarded-For의 첫 번째 IP를 사용한다")
    void usesFirstForwardedForAddressWhenProxyIsTrusted() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustedProxyEnabled(true);
        ClientIdentityResolver resolver = new ClientIdentityResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.10, 10.0.0.1");

        RateLimitIdentity identity = resolver.resolve(request);

        assertThat(identity.value()).isEqualTo("198.51.100.10");
    }
}
