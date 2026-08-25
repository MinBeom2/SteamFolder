package com.steamfolder.steamfolder.domain.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.steamfolder.steamfolder.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserResponse {

    private final Long id;
    private final String steamId;
    private final String username;
    private final String avatarUrl;
    private final String themeColor;

    @JsonProperty("isPublic")
    private final boolean isPublic;

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .steamId(user.getSteamId())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .themeColor(user.getThemeColor())
                .isPublic(user.isPublic())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
