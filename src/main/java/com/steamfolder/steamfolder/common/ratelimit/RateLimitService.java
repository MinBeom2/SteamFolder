package com.steamfolder.steamfolder.common.ratelimit;

public interface RateLimitService {

    RateLimitDecision consume(RateLimitPolicy policy, RateLimitIdentity identity);
}
