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
    private final long expiresAtMillis;
    private final Location anchor;
    private final boolean originalSwimming;
    private final Map<PotionEffectType, PotionEffect> previousEffects;
    private final Set<PotionEffectType> managedEffects;
    private long lastDisplayedSecond = Long.MIN_VALUE;

    public KnockoutSession(
            UUID playerId,
            long startedAtMillis,
            long expiresAtMillis,
            Location anchor,
            boolean originalSwimming,
            Map<PotionEffectType, PotionEffect> previousEffects,
            Set<PotionEffectType> managedEffects
    ) {
        this.playerId = playerId;
        this.startedAtMillis = startedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.anchor = anchor.clone();
        this.originalSwimming = originalSwimming;
        this.previousEffects = Collections.unmodifiableMap(new HashMap<>(previousEffects));
        this.managedEffects = Collections.unmodifiableSet(new HashSet<>(managedEffects));
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

    public boolean originalSwimming() {
        return originalSwimming;
    }

    public Map<PotionEffectType, PotionEffect> previousEffects() {
        return previousEffects;
    }

    public Set<PotionEffectType> managedEffects() {
        return managedEffects;
    }

    public long remainingSeconds(long now) {
        if (expiresAtMillis == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long millis = Math.max(0L, expiresAtMillis - now);
        return (millis + 999L) / 1000L;
    }

    public boolean shouldRefreshDisplay(long remainingSeconds) {
        if (lastDisplayedSecond == remainingSeconds) {
            return false;
        }
        lastDisplayedSecond = remainingSeconds;
        return true;
    }
}
