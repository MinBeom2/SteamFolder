package com.steamfolder.steamfolder.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

public class Bucket4jRateLimitService implements RateLimitService {

    private static final String KEY_PREFIX = "rate-limit:v1:";

    private final Supplier<ProxyManager<byte[]>> proxyManagerSupplier;
    private volatile ProxyManager<byte[]> proxyManager;

    public Bucket4jRateLimitService(Supplier<ProxyManager<byte[]>> proxyManagerSupplier) {
        this.proxyManagerSupplier = proxyManagerSupplier;
    }

    @Override
    public RateLimitDecision consume(RateLimitPolicy policy, RateLimitIdentity identity) {
        try {
            Bucket bucket = getProxyManager().getProxy(
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

    private ProxyManager<byte[]> getProxyManager() {
        ProxyManager<byte[]> current = proxyManager;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (proxyManager == null) {
                proxyManager = proxyManagerSupplier.get();
            }
            return proxyManager;
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
