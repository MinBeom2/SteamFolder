package com.steamfolder.steamfolder.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("한도 초과 요청은 429, Retry-After, 표준 오류 본문으로 종료한다")
    void rejectsRequestWhenLimitIsExceeded() throws Exception {
        RateLimitFilter filter = filter((policy, identity) -> new RateLimitDecision(
                false, policy, identity.type(), policy.getCapacity(), 0, 6
        ));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/steam/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, recordingChain(chainCalled));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("6");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getContentAsString()).contains("\"code\":\"RL001\"");
        assertThat(chainCalled).isFalse();
    }

    @Test
    @DisplayName("허용된 요청은 잔여 한도 헤더를 추가하고 다음 필터로 전달한다")
    void forwardsRequestWhenLimitAllowsIt() throws Exception {
        RateLimitFilter filter = filter((policy, identity) -> new RateLimitDecision(
                true, policy, identity.type(), policy.getCapacity(), 119, 0
        ));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rankings");
        request.setRemoteAddr("203.0.113.20");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, recordingChain(chainCalled));

        assertThat(chainCalled).isTrue();
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("120");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("119");
        assertThat(request.getAttribute(RateLimitFilter.DECISION_ATTRIBUTE)).isInstanceOf(RateLimitDecision.class);
    }

    @Test
    @DisplayName("Redis 장애 중 인증 요청은 503으로 차단한다")
    void blocksSensitiveRequestWhenRateLimitStoreIsUnavailable() throws Exception {
        RateLimitFilter filter = filter((policy, identity) -> {
            throw new RateLimitUnavailableException(new IllegalStateException("redis unavailable"));
        });
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, recordingChain(chainCalled));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("\"code\":\"RL002\"");
        assertThat(chainCalled).isFalse();
    }

    @Test
    @DisplayName("Redis 장애 중 조회 요청은 다음 필터로 전달한다")
    void forwardsReadRequestWhenRateLimitStoreIsUnavailable() throws Exception {
        RateLimitFilter filter = filter((policy, identity) -> {
            throw new RateLimitUnavailableException(new IllegalStateException("redis unavailable"));
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rankings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, recordingChain(chainCalled));

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("알 수 없는 HTTP 메서드는 쓰기 정책으로 안전하게 처리한다")
    void handlesUnknownHttpMethodAsWriteRequest() throws Exception {
        AtomicBoolean writePolicyUsed = new AtomicBoolean(false);
        RateLimitFilter filter = filter((policy, identity) -> {
            writePolicyUsed.set(policy == RateLimitPolicy.WRITE);
            return new RateLimitDecision(true, policy, identity.type(), policy.getCapacity(), 29, 0);
        });
        MockHttpServletRequest request = new MockHttpServletRequest("PROPFIND", "/api/v1/resources");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, recordingChain(chainCalled));

        assertThat(writePolicyUsed).isTrue();
        assertThat(chainCalled).isTrue();
    }

    private RateLimitFilter filter(RateLimitService service) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(true);
        return new RateLimitFilter(properties, new ClientIdentityResolver(properties), service,
                new ObjectMapper().findAndRegisterModules());
    }

    private FilterChain recordingChain(AtomicBoolean chainCalled) {
        return new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response)
                    throws IOException, ServletException {
                chainCalled.set(true);
            }
        };
    }
}
