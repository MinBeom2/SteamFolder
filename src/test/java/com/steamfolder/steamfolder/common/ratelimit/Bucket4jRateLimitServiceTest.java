package com.steamfolder.steamfolder.common.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Bucket4jRateLimitServiceTest {

    @Test
    @DisplayName("ProxyManager는 첫 요청까지 생성하지 않고 이후 재사용한다")
    @SuppressWarnings("unchecked")
    void initializesProxyManagerLazilyAndReusesIt() {
        ProxyManager<byte[]> proxyManager = mock(ProxyManager.class);
        BucketProxy bucket = mock(BucketProxy.class);
        when(proxyManager.getProxy(any(byte[].class), any(Supplier.class))).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(ConsumptionProbe.consumed(9, 0));
        AtomicInteger initializationCount = new AtomicInteger();
        Bucket4jRateLimitService service = new Bucket4jRateLimitService(() -> {
            initializationCount.incrementAndGet();
            return proxyManager;
        });

        assertThat(initializationCount).hasValue(0);

        RateLimitDecision first = service.consume(
                RateLimitPolicy.AUTH,
                new RateLimitIdentity(RateLimitIdentityType.IP, "203.0.113.10")
        );
        RateLimitDecision second = service.consume(
                RateLimitPolicy.AUTH,
                new RateLimitIdentity(RateLimitIdentityType.IP, "203.0.113.10")
        );

        assertThat(first.allowed()).isTrue();
        assertThat(first.remainingTokens()).isEqualTo(9);
        assertThat(second.allowed()).isTrue();
        assertThat(initializationCount).hasValue(1);
    }

    @Test
    @DisplayName("Redis 연결 실패는 RateLimitUnavailableException으로 변환하고 다음 요청에서 재시도한다")
    void retriesInitializationAfterRedisFailure() {
        AtomicInteger initializationCount = new AtomicInteger();
        Bucket4jRateLimitService service = new Bucket4jRateLimitService(() -> {
            initializationCount.incrementAndGet();
            throw new IllegalStateException("redis unavailable");
        });
        RateLimitIdentity identity = new RateLimitIdentity(RateLimitIdentityType.IP, "203.0.113.10");

        assertThatThrownBy(() -> service.consume(RateLimitPolicy.AUTH, identity))
                .isInstanceOf(RateLimitUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.consume(RateLimitPolicy.AUTH, identity))
                .isInstanceOf(RateLimitUnavailableException.class);

        assertThat(initializationCount).hasValue(2);
    }
}
