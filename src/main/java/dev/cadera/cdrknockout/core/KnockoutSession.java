package dev.cadera.cdrknockout.core;

import org.bukkit.Location;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KnockoutSession {

    private final UUID playerId;
    private final long startedAtMillis;
    private long expiresAtMillis;
    private Location anchor;
    private final boolean originalSwimming;
    private final Map<PotionEffectType, PotionEffect> previousEffects;
    private final Set<PotionEffectType> managedEffects;
    private final Set<Integer> sentWarningThresholds = new HashSet<>();
    private long lastDisplayedSecond = Long.MIN_VALUE;
    private long lastHeartbeatMillis = Long.MIN_VALUE;
    private long offlineSinceMillis;

    public KnockoutSession(
            UUID playerId,
            long startedAtMillis,
            long expiresAtMillis,
            Location anchor,
            boolean originalSwimming,
            Map<PotionEffectType, PotionEffect> previousEffects,
            Set<PotionEffectType> managedEffects
    ) {
        this(playerId, startedAtMillis, expiresAtMillis, anchor, originalSwimming,
                previousEffects, managedEffects, 0L);
    }

    public KnockoutSession(
            UUID playerId,
            long startedAtMillis,
            long expiresAtMillis,
            Location anchor,
            boolean originalSwimming,
            Map<PotionEffectType, PotionEffect> previousEffects,
            Set<PotionEffectType> managedEffects,
            long offlineSinceMillis
    ) {
        this.playerId = playerId;
        this.startedAtMillis = startedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.anchor = anchor.clone();
        this.originalSwimming = originalSwimming;
        this.previousEffects = Collections.unmodifiableMap(new HashMap<>(previousEffects));
        this.managedEffects = Collections.unmodifiableSet(new HashSet<>(managedEffects));
        this.offlineSinceMillis = Math.max(0L, offlineSinceMillis);
    }

    public UUID playerId() {
        return playerId;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public Location anchor() {
        return anchor.clone();
    }

    public void updateAnchor(Location location) {
        if (location != null) {
            this.anchor = location.clone();
        }
    }

    public boolean originalSwimming() {
        return originalSwimming;
    }

    public Map<PotionEffectType, PotionEffect> previousEffects() {
        return previousEffects;
    }

    public Set<PotionEffectType> managedEffects() {
        return managedEffects;
    }

    public long offlineSinceMillis() {
        return offlineSinceMillis;
    }

    public void markOffline(long now) {
        if (offlineSinceMillis <= 0L) {
            offlineSinceMillis = Math.max(1L, now);
        }
    }

    public void resume(long now, boolean offlineTimeCounts) {
        if (offlineSinceMillis <= 0L) {
            return;
        }
        if (!offlineTimeCounts && expiresAtMillis != Long.MAX_VALUE) {
            long pausedFor = Math.max(0L, now - offlineSinceMillis);
            if (Long.MAX_VALUE - expiresAtMillis < pausedFor) {
                expiresAtMillis = Long.MAX_VALUE;
            } else {
                expiresAtMillis += pausedFor;
            }
        }
        offlineSinceMillis = 0L;
        lastDisplayedSecond = Long.MIN_VALUE;
        lastHeartbeatMillis = Long.MIN_VALUE;
    }

    public long remainingSeconds(long now) {
        if (expiresAtMillis == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long millis = Math.max(0L, expiresAtMillis - now);
        return (millis + 999L) / 1000L;
    }

    public long reduceRemainingMillis(long millis, long now) {
        if (expiresAtMillis == Long.MAX_VALUE || millis <= 0L) {
            return remainingSeconds(now);
        }
        expiresAtMillis = Math.max(now, expiresAtMillis - millis);
        lastDisplayedSecond = Long.MIN_VALUE;
        return remainingSeconds(now);
    }

    public boolean markWarningSent(int threshold) {
        return sentWarningThresholds.add(threshold);
    }

    public boolean shouldHeartbeat(long now, long intervalMillis) {
        if (lastHeartbeatMillis == Long.MIN_VALUE || now - lastHeartbeatMillis >= intervalMillis) {
            lastHeartbeatMillis = now;
            return true;
        }
        return false;
    }

    public boolean shouldRefreshDisplay(long remainingSeconds) {
        if (lastDisplayedSecond == remainingSeconds) {
            return false;
        }
        lastDisplayedSecond = remainingSeconds;
        return true;
    }
}
