package com.steamfolder.steamfolder.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenRefreshResponse {

    private final String accessToken;
    private final String refreshToken;
}
