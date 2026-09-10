package com.steamfolder.steamfolder.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RequiredArgsConstructor
public class Bucket4jRateLimitService implements RateLimitService {

    private static final String KEY_PREFIX = "rate-limit:v1:";

    private final ProxyManager<byte[]> proxyManager;

    @Override
    public RateLimitDecision consume(RateLimitPolicy policy, RateLimitIdentity identity) {
        try {
            Bucket bucket = proxyManager.getProxy(
                    bucketKey(policy, identity).getBytes(StandardCharsets.UTF_8),
                    () -> configurationFor(policy)
            );
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            long retryAfterSeconds = probe.isConsumed()
                    ? 0
                    : Math.max(1, (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));

            return new RateLimitDecision(
                    probe.isConsumed(),
                    policy,
                    identity.type(),
                    policy.getCapacity(),
                    probe.getRemainingTokens(),
                    retryAfterSeconds
            );
        } catch (RuntimeException exception) {
            throw new RateLimitUnavailableException(exception);
        }
    }

    private String bucketKey(RateLimitPolicy policy, RateLimitIdentity identity) {
        return KEY_PREFIX + policy.name().toLowerCase() + ":" + identity.bucketKeyPart();
    }

    private BucketConfiguration configurationFor(RateLimitPolicy policy) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(policy.getCapacity())
                .refillGreedy(policy.getRefillTokensPerMinute(), Duration.ofMinutes(1))
                .build();
        return BucketConfiguration.builder().addLimit(limit).build();
    }
}
