package com.steamfolder.steamfolder.domain.folder.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolderResponse {

    private Long id;
    private String name;
    private String themeColor;
    private String icon;
    private int positionX;
    private int positionY;
    private int depth;

    @JsonProperty("isOpen")
    private boolean isOpen;

    private Long parentFolderId;
    private int gameCount;
    private int childFolderCount;

    @Builder.Default
    private List<BreadcrumbDto> breadcrumbs = new ArrayList<>();

    @Builder.Default
    private List<FolderResponse> childFolders = new ArrayList<>();

    @Builder.Default
    private List<FolderGameDto> games = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BreadcrumbDto {
        private Long id;
        private String name;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FolderGameDto {
        private Long id; // folderGameId
        private Long appId;
        private String name;
        private String headerImage;
        private int positionX;
        private int positionY;
        private List<String> genres;
        private Integer playtimeForever;
        private LocalDateTime lastPlayedAt;
    }

    /**
     * 폴더 기본 정보 변환 (직속 게임 목록 포함하여 2x2 미리보기 지원)
     */
    public static FolderResponse fromSimple(Folder folder) {
        return fromSimple(folder, Collections.emptyMap());
    }

    public static FolderResponse fromSimple(Folder folder, Map<Long, UserGame> userGameMap) {
        int gameSize = folder.getFolderGames() != null ? folder.getFolderGames().size() : 0;
        int childSize = folder.getChildFolders() != null ? folder.getChildFolders().size() : 0;

        List<FolderGameDto> gameResponses = new ArrayList<>();
        if (folder.getFolderGames() != null) {
            gameResponses = folder.getFolderGames().stream()
                    .map(fg -> {
                        UserGame ug = userGameMap != null ? userGameMap.get(fg.getGame().getAppId()) : null;
                        return FolderGameDto.builder()
                                .id(fg.getId())
                                .appId(fg.getGame().getAppId())
                                .name(fg.getGame().getName())
                                .headerImage(fg.getGame().getHeaderImage())
                                .positionX(fg.getPositionX())
                                .positionY(fg.getPositionY())
                                .genres(fg.getGame().getGenres())
                                .playtimeForever(ug != null ? ug.getPlaytimeForever() : null)
                                .lastPlayedAt(ug != null ? ug.getLastPlayedAt() : null)
                                .build();
                    })
                    .toList();
        }

        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .themeColor(folder.getThemeColor())
                .icon(folder.getIcon())
                .positionX(folder.getPositionX())
                .positionY(folder.getPositionY())
                .depth(folder.getDepth())
                .isOpen(folder.isOpen())
                .parentFolderId(folder.getParentFolder() != null ? folder.getParentFolder().getId() : null)
                .gameCount(gameSize)
                .childFolderCount(childSize)
                .breadcrumbs(List.of())
                .childFolders(List.of())
                .games(gameResponses)
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }

    /**
     * 특정 폴더 상세 변환 (상위 경로 Breadcrumbs + 직속 자식 폴더 목록 + 직속 게임 목록 포함)
     */
    public static FolderResponse from(Folder folder) {
        return from(folder, Collections.emptyMap());
    }

    public static FolderResponse from(Folder folder, Map<Long, UserGame> userGameMap) {
        int gameSize = folder.getFolderGames() != null ? folder.getFolderGames().size() : 0;
        int childSize = folder.getChildFolders() != null ? folder.getChildFolders().size() : 0;

        // 상위 부모 폴더 경로(Breadcrumbs) 탐색
        List<BreadcrumbDto> breadcrumbList = new ArrayList<>();
        Folder current = folder.getParentFolder();
        while (current != null) {
            breadcrumbList.add(0, BreadcrumbDto.builder()
                    .id(current.getId())
                    .name(current.getName())
                    .build());
            current = current.getParentFolder();
        }

        // 직속 1단계 자식 폴더들 (자식의 자식은 재귀하지 않고 가볍게 기본 정보만)
        List<FolderResponse> directChildren = new ArrayList<>();
        if (folder.getChildFolders() != null) {
            directChildren = folder.getChildFolders().stream()
                    .map(child -> FolderResponse.fromSimple(child, userGameMap))
                    .toList();
        }

        // 직속 게임 목록
        List<FolderGameDto> gameResponses = new ArrayList<>();
        if (folder.getFolderGames() != null) {
            gameResponses = folder.getFolderGames().stream()
                    .map(fg -> {
                        UserGame ug = userGameMap != null ? userGameMap.get(fg.getGame().getAppId()) : null;
                        return FolderGameDto.builder()
                                .id(fg.getId())
                                .appId(fg.getGame().getAppId())
                                .name(fg.getGame().getName())
                                .headerImage(fg.getGame().getHeaderImage())
                                .positionX(fg.getPositionX())
                                .positionY(fg.getPositionY())
                                .genres(fg.getGame().getGenres())
                                .playtimeForever(ug != null ? ug.getPlaytimeForever() : null)
                                .lastPlayedAt(ug != null ? ug.getLastPlayedAt() : null)
                                .build();
                    })
                    .toList();
        }

        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .themeColor(folder.getThemeColor())
                .icon(folder.getIcon())
                .positionX(folder.getPositionX())
                .positionY(folder.getPositionY())
                .depth(folder.getDepth())
                .isOpen(folder.isOpen())
                .parentFolderId(folder.getParentFolder() != null ? folder.getParentFolder().getId() : null)
                .gameCount(gameSize)
                .childFolderCount(childSize)
                .breadcrumbs(breadcrumbList)
                .childFolders(directChildren)
                .games(gameResponses)
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }
}
