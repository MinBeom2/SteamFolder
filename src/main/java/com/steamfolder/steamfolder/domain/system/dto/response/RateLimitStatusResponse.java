package com.steamfolder.steamfolder.domain.system.dto.response;

import com.steamfolder.steamfolder.common.ratelimit.RateLimitDecision;
import com.steamfolder.steamfolder.common.ratelimit.RateLimitIdentityType;
import com.steamfolder.steamfolder.common.ratelimit.RateLimitPolicy;

public record RateLimitStatusResponse(
        RateLimitPolicy policy,
        RateLimitIdentityType identityType,
        long limit,
        long remainingRequests,
        long retryAfterSeconds
) {
    public static RateLimitStatusResponse from(RateLimitDecision decision) {
        return new RateLimitStatusResponse(
                decision.policy(),
                decision.identityType(),
                decision.limit(),
                decision.remainingTokens(),
                decision.retryAfterSeconds()
        );
    }
}
