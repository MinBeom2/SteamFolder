package com.steamfolder.steamfolder.domain.feedback.sync;

import com.steamfolder.steamfolder.common.security.jwt.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(FeedbackNotionSyncProperties.class)
public class FeedbackNotionSyncSecurityConfig {

    private final FeedbackNotionSyncProperties properties;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    @Order(1)
    public SecurityFilterChain feedbackNotionSyncFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/internal/v1/feedback-notion-sync/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("N8N"))
                .addFilterBefore(new FeedbackNotionSyncAuthenticationFilter(properties),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
