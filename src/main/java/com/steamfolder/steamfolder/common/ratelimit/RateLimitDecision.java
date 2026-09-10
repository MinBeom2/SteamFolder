package com.steamfolder.steamfolder.common.ratelimit;

public record RateLimitDecision(
        boolean allowed,
        RateLimitPolicy policy,
        RateLimitIdentityType identityType,
        long limit,
        long remainingTokens,
        long retryAfterSeconds
) {
}
