package com.steamfolder.steamfolder.domain.user.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UpdateVisibilityRequest {

    @NotNull(message = "공개 여부(isPublic)는 필수 값입니다.")
    private Boolean isPublic;
}
