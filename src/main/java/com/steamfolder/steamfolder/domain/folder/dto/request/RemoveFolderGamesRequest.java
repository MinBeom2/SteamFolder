package com.steamfolder.steamfolder.domain.folder.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemoveFolderGamesRequest {

    @NotEmpty(message = "삭제할 게임 아이템 ID(folderGameId) 목록은 비어있을 수 없습니다.")
    private List<Long> folderGameIds;
}
