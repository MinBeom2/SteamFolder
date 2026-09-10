package com.steamfolder.steamfolder.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.cluster.RedisClusterClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    @ConditionalOnProperty(prefix = "rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ProxyManager<byte[]> rateLimitProxyManager(LettuceConnectionFactory connectionFactory) {
        AbstractRedisClient client = connectionFactory.getRequiredNativeClient();
        ExpirationAfterWriteStrategy expiration = ExpirationAfterWriteStrategy
                .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1));

        if (client instanceof RedisClient redisClient) {
            return Bucket4jLettuce.casBasedBuilder(redisClient)
                    .expirationAfterWrite(expiration)
                    .maxRetries(5)
                    .build();
        }
        if (client instanceof RedisClusterClient redisClusterClient) {
            return Bucket4jLettuce.casBasedBuilder(redisClusterClient)
                    .expirationAfterWrite(expiration)
                    .maxRetries(5)
                    .build();
        }
        throw new IllegalStateException("지원하지 않는 Lettuce Redis 클라이언트입니다.");
    }

    @Bean
    @ConditionalOnProperty(prefix = "rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitService bucket4jRateLimitService(ProxyManager<byte[]> rateLimitProxyManager) {
        return new Bucket4jRateLimitService(rateLimitProxyManager);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitService.class)
    public RateLimitService disabledRateLimitService() {
        return (policy, identity) -> new RateLimitDecision(
                true,
                policy,
                identity.type(),
                policy.getCapacity(),
                policy.getCapacity(),
                0
        );
    }

    @Bean
    public ClientIdentityResolver clientIdentityResolver(RateLimitProperties properties) {
        return new ClientIdentityResolver(properties);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(
            RateLimitProperties properties,
            ClientIdentityResolver clientIdentityResolver,
            RateLimitService rateLimitService,
            ObjectMapper objectMapper
    ) {
        return new RateLimitFilter(properties, clientIdentityResolver, rateLimitService, objectMapper);
    }
}
