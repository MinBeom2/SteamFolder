package com.steamfolder.steamfolder.domain.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private final String accessToken;
    private final String refreshToken;
    private final Long userId;
    private final String steamId;
    private final String username;
    private final String avatarUrl;
    private final String themeColor;

    @JsonProperty("isNewUser")
    private final boolean isNewUser;
}
