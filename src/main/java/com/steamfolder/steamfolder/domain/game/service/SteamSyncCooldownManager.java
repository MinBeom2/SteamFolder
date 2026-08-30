package com.steamfolder.steamfolder.domain.game.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SteamSyncCooldownManager {

    private static final long COOLDOWN_SECONDS = 300; // 5 minutes

    private final Map<String, Long> cooldownMap = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastSyncedMap = new ConcurrentHashMap<>();

    public void validateCooldown(String steamId) {
        long remainingSeconds = getRemainingSeconds(steamId);
        if (remainingSeconds > 0) {
            log.warn("Sync cooldown active for steamId: {}. Remaining seconds: {}", steamId, remainingSeconds);
            throw new CustomException(ErrorCode.SYNC_COOLDOWN_ACTIVE);
        }
    }

    public long getRemainingSeconds(String steamId) {
        Long expiryTime = cooldownMap.get(steamId);
        if (expiryTime == null) {
            return 0;
        }

        long remaining = (expiryTime - System.currentTimeMillis()) / 1000;
        if (remaining <= 0) {
            cooldownMap.remove(steamId);
            return 0;
        }
        return remaining;
    }

    public void applyCooldown(String steamId) {
        cooldownMap.put(steamId, System.currentTimeMillis() + (COOLDOWN_SECONDS * 1000));
        lastSyncedMap.put(steamId, LocalDateTime.now());
    }

    public LocalDateTime getLastSyncedAt(String steamId) {
        return lastSyncedMap.get(steamId);
    }
}
