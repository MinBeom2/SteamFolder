package com.steamfolder.steamfolder.domain.folder.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFolderRequest {

    @NotBlank(message = "폴더명은 필수입니다.")
    @Size(max = 100, message = "폴더명은 100자 이하이어야 합니다.")
    private String name;

    private Long parentFolderId;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "테마 색상은 올바른 6자리 HEX 코드(예: #1A1B26)이어야 합니다.")
    private String themeColor;

    private String icon;

    private Integer positionX;
    private Integer positionY;
}
