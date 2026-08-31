package com.steamfolder.steamfolder.domain.folder.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private List<FolderResponse> childFolders = new ArrayList<>();

    @Builder.Default
    private List<FolderGameDto> games = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
    }

    /**
     * 폴더 기본 정보 변환 (자식 폴더/게임 컬렉션 미포함)
     */
    public static FolderResponse fromSimple(Folder folder) {
        int gameSize = folder.getFolderGames() != null ? folder.getFolderGames().size() : 0;
        int childSize = folder.getChildFolders() != null ? folder.getChildFolders().size() : 0;

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
                .childFolders(List.of())
                .games(List.of())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }

    /**
     * 특정 폴더 상세 변환 (직속 1단계 자식 폴더 목록 + 직속 게임 목록 포함)
     */
    public static FolderResponse from(Folder folder) {
        int gameSize = folder.getFolderGames() != null ? folder.getFolderGames().size() : 0;
        int childSize = folder.getChildFolders() != null ? folder.getChildFolders().size() : 0;

        // 직속 1단계 자식 폴더들 (자식의 자식은 재귀하지 않고 가볍게 기본 정보만)
        List<FolderResponse> directChildren = new ArrayList<>();
        if (folder.getChildFolders() != null) {
            directChildren = folder.getChildFolders().stream()
                    .map(FolderResponse::fromSimple)
                    .toList();
        }

        // 직속 게임 목록
        List<FolderGameDto> gameResponses = new ArrayList<>();
        if (folder.getFolderGames() != null) {
            gameResponses = folder.getFolderGames().stream()
                    .map(fg -> FolderGameDto.builder()
                            .id(fg.getId())
                            .appId(fg.getGame().getAppId())
                            .name(fg.getGame().getName())
                            .headerImage(fg.getGame().getHeaderImage())
                            .positionX(fg.getPositionX())
                            .positionY(fg.getPositionY())
                            .genres(fg.getGame().getGenres())
                            .build())
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
                .childFolders(directChildren)
                .games(gameResponses)
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }
}
