package com.steamfolder.steamfolder.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.steamfolder.steamfolder.common.dto.ErrorResponse;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String DECISION_ATTRIBUTE = RateLimitFilter.class.getName() + ".decision";

    private final RateLimitProperties properties;
    private final ClientIdentityResolver identityResolver;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled()
                || !request.getRequestURI().startsWith("/api/v1/")
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RateLimitPolicy policy = RateLimitPolicy.resolve(request.getMethod(), request.getRequestURI());
        try {
            RateLimitDecision decision = rateLimitService.consume(policy, identityResolver.resolve(request, policy));
            request.setAttribute(DECISION_ATTRIBUTE, decision);
            writeRateLimitHeaders(response, decision);

            if (!decision.allowed()) {
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
                writeError(response, ErrorCode.RATE_LIMIT_EXCEEDED);
                return;
            }
            filterChain.doFilter(request, response);
        } catch (RateLimitUnavailableException exception) {
            if (policy.isRejectWhenUnavailable()) {
                log.error("레이트 리밋 저장소를 사용할 수 없어 요청을 차단합니다. uri={}", request.getRequestURI(), exception);
                writeError(response, ErrorCode.RATE_LIMIT_SERVICE_UNAVAILABLE);
                return;
            }
            log.warn("레이트 리밋 저장소를 사용할 수 없어 조회 요청을 통과시킵니다. uri={}", request.getRequestURI(), exception);
            filterChain.doFilter(request, response);
        }
    }

    private void writeRateLimitHeaders(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader("X-RateLimit-Limit", Long.toString(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remainingTokens()));
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(errorCode));
    }
}
