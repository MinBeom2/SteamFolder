package com.steamfolder.steamfolder.domain.folder.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePositionsRequest {

    @NotEmpty(message = "위치 변경 대상 목록은 비어있을 수 없습니다.")
    @Valid
    private List<ItemPositionDto> positions;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemPositionDto {
        @NotNull(message = "아이템 타입은 필수입니다 (FOLDER 또는 GAME).")
        private String type; // FOLDER or GAME

        @NotNull(message = "아이템 ID는 필수입니다.")
        private Long id; // folderId or folderGameId

        private int positionX;
        private int positionY;
    }
}
