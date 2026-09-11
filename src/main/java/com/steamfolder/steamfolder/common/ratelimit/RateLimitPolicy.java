package com.steamfolder.steamfolder.common.ratelimit;

import org.springframework.http.HttpMethod;

/**
 * API group별 토큰 버킷 정책이다. 모든 정책은 1분 동안 용량만큼 균등하게 충전된다.
 */
public enum RateLimitPolicy {

    AUTH(10, true),
    STEAM_SYNC(10, true),
    WRITE(30, true),
    READ(120, false);

    private final long capacity;
    private final boolean rejectWhenUnavailable;

    RateLimitPolicy(long capacity, boolean rejectWhenUnavailable) {
        this.capacity = capacity;
        this.rejectWhenUnavailable = rejectWhenUnavailable;
    }

    public long getCapacity() {
        return capacity;
    }

    public long getRefillTokensPerMinute() {
        return capacity;
    }

    public boolean isRejectWhenUnavailable() {
        return rejectWhenUnavailable;
    }

    public static RateLimitPolicy resolve(HttpMethod method, String requestUri) {
        return resolve(method.name(), requestUri);
    }

    public static RateLimitPolicy resolve(String method, String requestUri) {
        if (requestUri.startsWith("/api/v1/auth/")) {
            return AUTH;
        }
        if (HttpMethod.POST.matches(method) && isSteamSyncRequest(requestUri)) {
            return STEAM_SYNC;
        }
        if (!HttpMethod.GET.matches(method)
                && !HttpMethod.HEAD.matches(method)
                && !HttpMethod.OPTIONS.matches(method)) {
            return WRITE;
        }
        return READ;
    }

    private static boolean isSteamSyncRequest(String requestUri) {
        return "/api/v1/steam/sync".equals(requestUri)
                || (requestUri.startsWith("/api/v1/users/me/games/")
                && requestUri.endsWith("/achievements/sync"));
    }
}
