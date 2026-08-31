package com.steamfolder.steamfolder.domain.folder.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoveFolderGameRequest {

    @NotNull(message = "이동할 대상 폴더 ID는 필수입니다.")
    private Long targetFolderId;

    private Integer targetPositionX;
    private Integer targetPositionY;
}
