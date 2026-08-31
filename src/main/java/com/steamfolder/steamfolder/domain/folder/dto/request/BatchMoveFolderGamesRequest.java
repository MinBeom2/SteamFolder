package com.steamfolder.steamfolder.domain.folder.dto.request;

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
public class BatchMoveFolderGamesRequest {

    @NotEmpty(message = "이동할 게임 아이템 ID(folderGameId) 목록은 비어있을 수 없습니다.")
    private List<Long> folderGameIds;

    @NotNull(message = "이동할 대상 폴더 ID는 필수입니다.")
    private Long targetFolderId;
}
