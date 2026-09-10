package com.steamfolder.steamfolder.common.ratelimit;

public record RateLimitIdentity(RateLimitIdentityType type, String value) {

    public String bucketKeyPart() {
        return type.name().toLowerCase() + ":" + value;
    }
}
