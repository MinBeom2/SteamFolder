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
public class AddFolderGameRequest {

    @NotNull(message = "게임 AppID는 필수입니다.")
    private Long appId;

    private Integer positionX;
    private Integer positionY;
}
