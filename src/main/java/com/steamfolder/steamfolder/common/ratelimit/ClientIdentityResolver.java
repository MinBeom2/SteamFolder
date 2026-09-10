package com.steamfolder.steamfolder.common.ratelimit;

import com.steamfolder.steamfolder.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

@RequiredArgsConstructor
public class ClientIdentityResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final RateLimitProperties properties;

    public RateLimitIdentity resolve(HttpServletRequest request) {
        return resolve(request, null);
    }

    public RateLimitIdentity resolve(HttpServletRequest request, RateLimitPolicy policy) {
        if (policy == RateLimitPolicy.AUTH) {
            return new RateLimitIdentity(RateLimitIdentityType.IP, resolveClientIp(request));
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal
                && principal.getUserId() != null) {
            return new RateLimitIdentity(RateLimitIdentityType.USER, principal.getUserId().toString());
        }

        return new RateLimitIdentity(RateLimitIdentityType.IP, resolveClientIp(request));
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (properties.isTrustedProxyEnabled()) {
            String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
            if (StringUtils.hasText(forwardedFor)) {
                return forwardedFor.split(",", 2)[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
