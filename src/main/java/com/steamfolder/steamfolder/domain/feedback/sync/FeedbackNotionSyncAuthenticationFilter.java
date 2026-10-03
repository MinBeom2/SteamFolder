package com.steamfolder.steamfolder.domain.feedback.sync;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RequiredArgsConstructor
public class FeedbackNotionSyncAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final FeedbackNotionSyncProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (properties.isEnabled() && StringUtils.hasText(authorization)
                && authorization.startsWith(BEARER_PREFIX)) {
            String provided = authorization.substring(BEARER_PREFIX.length());
            byte[] actual = provided.getBytes(StandardCharsets.UTF_8);
            byte[] expected = properties.getToken().getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(actual, expected)) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "n8n", null, List.of(new SimpleGrantedAuthority("ROLE_N8N"))
                        )
                );
            }
        }
        filterChain.doFilter(request, response);
    }
}
