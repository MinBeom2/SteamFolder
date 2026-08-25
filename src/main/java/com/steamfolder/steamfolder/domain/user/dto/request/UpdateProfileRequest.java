package com.steamfolder.steamfolder.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @NotBlank(message = "테마 색상은 필수 입력값입니다.")
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "올바른 6자리 HEX 색상 코드여야 합니다. (예: #1A1B26)")
    private String themeColor;
}
