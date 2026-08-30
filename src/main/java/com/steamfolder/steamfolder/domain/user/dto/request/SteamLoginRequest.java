package com.steamfolder.steamfolder.domain.user.dto.request;

import lombok.*;

import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SteamLoginRequest {

    // 스팀 OpenID 2.0 파라미터 맵 (웹 리다이렉트 로그인 시 사용)
    private Map<String, String> openIdParams;

    // 간편 로그인/테스트용 직접 claimedId 또는 steamId 입력 지원
    private String steamId;
    private String username;
    private String avatarUrl;
}
