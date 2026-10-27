package com.steamfolder.steamfolder.domain.folder.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CopyFolderRequest {

    @Positive(message = "대상 폴더 ID는 양수여야 합니다.")
    private Long targetParentFolderId;

    @Size(min = 1, max = 100, message = "폴더명은 1자 이상 100자 이하여야 합니다.")
    private String name;

    @Min(value = 0, message = "좌표는 0 이상이어야 합니다.")
    private Integer targetPositionX;

    @Min(value = 0, message = "좌표는 0 이상이어야 합니다.")
    private Integer targetPositionY;
}
