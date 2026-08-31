package com.steamfolder.steamfolder.domain.folder.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoveFolderRequest {

    private Long targetParentFolderId;
    private Integer targetPositionX;
    private Integer targetPositionY;
}
