package com.steamfolder.steamfolder.domain.user.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SteamLoginRequest {

    @NotEmpty(message = "스팀 OpenID 인증 파라미터는 필수입니다.")
    private Map<String, String> openIdParams;

    // 간편 로그인/테스트용 직접 claimedId 또는 steamId 입력 지원
    private String steamId;
    private String username;
    private String avatarUrl;
}
