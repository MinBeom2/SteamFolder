package com.steamfolder.steamfolder.domain.game.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CooldownResponseDto {
    private boolean isCooldownActive;
    private long remainingSeconds;
    private LocalDateTime lastSyncedAt;
}
