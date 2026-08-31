package com.steamfolder.steamfolder.domain.folder.dto.response;

import com.steamfolder.steamfolder.domain.folder.entity.FolderGame;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolderGameDetailResponse {

    private Long id; // folderGameId
    private Long folderId;
    private Long appId;
    private String name;
    private String headerImage;
    private int positionX;
    private int positionY;
    private List<String> genres;
    private LocalDateTime createdAt;

    public static FolderGameDetailResponse from(FolderGame folderGame) {
        return FolderGameDetailResponse.builder()
                .id(folderGame.getId())
                .folderId(folderGame.getFolder().getId())
                .appId(folderGame.getGame().getAppId())
                .name(folderGame.getGame().getName())
                .headerImage(folderGame.getGame().getHeaderImage())
                .positionX(folderGame.getPositionX())
                .positionY(folderGame.getPositionY())
                .genres(folderGame.getGame().getGenres())
                .createdAt(folderGame.getCreatedAt())
                .build();
    }
}
