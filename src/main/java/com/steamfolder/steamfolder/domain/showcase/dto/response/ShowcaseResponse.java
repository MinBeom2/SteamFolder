package com.steamfolder.steamfolder.domain.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.steamfolder.steamfolder.domain.folder.dto.response.FolderResponse;
import com.steamfolder.steamfolder.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowcaseResponse {

    private ShowcaseUserDto user;
    private ShowcaseStatsDto stats;
    private List<FolderResponse> rootFolders;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShowcaseUserDto {
        private Long id;
        private String steamId;
        private String username;
        private String avatarUrl;
        private String themeColor;

        @JsonProperty("isPublic")
        private boolean isPublic;

        public static ShowcaseUserDto from(User user) {
            return ShowcaseUserDto.builder()
                    .id(user.getId())
                    .steamId(user.getSteamId())
                    .username(user.getUsername())
                    .avatarUrl(user.getAvatarUrl())
                    .themeColor(user.getThemeColor())
                    .isPublic(user.isPublic())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShowcaseStatsDto {
        private int totalFolders;
        private int totalGamesInFolders;
        private int totalOwnedGames;
        private int totalPlaytimeForever; // 분 단위
    }
}
