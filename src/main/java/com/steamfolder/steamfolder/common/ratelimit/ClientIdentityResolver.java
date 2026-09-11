package com.steamfolder.steamfolder.common.ratelimit;

import com.steamfolder.steamfolder.common.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public class ClientIdentityResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final List<IpAddressMatcher> trustedProxyMatchers;

    public ClientIdentityResolver(RateLimitProperties properties) {
        this.trustedProxyMatchers = properties.getTrustedProxyCidrs().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(IpAddressMatcher::new)
                .toList();
    }

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
        if (isTrustedProxy(request.getRemoteAddr())) {
            String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
            if (StringUtils.hasText(forwardedFor)) {
                String forwardedAddress = normalizeIpAddress(forwardedFor.split(",", 2)[0].trim());
                if (forwardedAddress != null) {
                    return forwardedAddress;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private boolean isTrustedProxy(String remoteAddress) {
        return StringUtils.hasText(remoteAddress)
                && trustedProxyMatchers.stream().anyMatch(matcher -> matcher.matches(remoteAddress));
    }

    private String normalizeIpAddress(String address) {
        try {
            if (!isNumericIpAddress(address)) {
                return null;
            }
            return InetAddress.getByName(address).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private boolean isNumericIpAddress(String address) {
        if (address.indexOf(':') >= 0) {
            return address.matches("[0-9a-fA-F:.]+");
        }

        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || !octet.chars().allMatch(Character::isDigit)) {
                return false;
            }
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }
}
