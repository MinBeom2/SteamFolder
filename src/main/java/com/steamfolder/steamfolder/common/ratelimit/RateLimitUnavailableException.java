package com.steamfolder.steamfolder.common.ratelimit;

public class RateLimitUnavailableException extends RuntimeException {

    public RateLimitUnavailableException(Throwable cause) {
        super(cause);
    }
}
