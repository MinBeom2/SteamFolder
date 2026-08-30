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
public class SyncResultResponseDto {
    private int syncedGameCount;
    private int newlyAddedCount;
    private LocalDateTime lastSyncedAt;
}
