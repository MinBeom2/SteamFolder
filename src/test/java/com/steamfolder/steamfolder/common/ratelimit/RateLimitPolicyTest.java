package com.steamfolder.steamfolder.common.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPolicyTest {

    @Test
    @DisplayName("인증, Steam 동기화, 쓰기, 조회 요청에 각각의 정책을 적용한다")
    void resolvesPoliciesByEndpointAndMethod() {
        assertThat(RateLimitPolicy.resolve(HttpMethod.POST, "/api/v1/auth/steam/login"))
                .isEqualTo(RateLimitPolicy.AUTH);
        assertThat(RateLimitPolicy.resolve(HttpMethod.POST, "/api/v1/steam/sync"))
                .isEqualTo(RateLimitPolicy.STEAM_SYNC);
        assertThat(RateLimitPolicy.resolve(HttpMethod.POST, "/api/v1/users/me/games/730/achievements/sync"))
                .isEqualTo(RateLimitPolicy.STEAM_SYNC);
        assertThat(RateLimitPolicy.resolve(HttpMethod.POST, "/api/v1/feedbacks"))
                .isEqualTo(RateLimitPolicy.WRITE);
        assertThat(RateLimitPolicy.resolve(HttpMethod.GET, "/api/v1/rankings"))
                .isEqualTo(RateLimitPolicy.READ);
        assertThat(RateLimitPolicy.resolve("PROPFIND", "/api/v1/resources"))
                .isEqualTo(RateLimitPolicy.WRITE);
    }
}
