package com.steamfolder.steamfolder.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String GAME_ENRICHMENT_EXECUTOR = "gameEnrichmentExecutor";

    @Bean(name = GAME_ENRICHMENT_EXECUTOR)
    public Executor gameEnrichmentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("SteamEnrich-");
        executor.initialize();
        return executor;
    }
}
