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
public class BatchAddFolderGamesRequest {

    @NotEmpty(message = "추가할 게임 AppID 목록은 비어있을 수 없습니다.")
    private List<Long> appIds;
}
