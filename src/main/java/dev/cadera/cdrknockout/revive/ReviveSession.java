package dev.cadera.cdrknockout.revive;

import org.bukkit.Location;

import java.util.UUID;

public final class ReviveSession {

    private final UUID targetId;
    private final UUID reviverId;
    private final long startedAtMillis;
    private final long completesAtMillis;
    private final Location reviverStartLocation;

    public ReviveSession(
            UUID targetId,
            UUID reviverId,
            long startedAtMillis,
            long completesAtMillis,
            Location reviverStartLocation
    ) {
        this.targetId = targetId;
        this.reviverId = reviverId;
        this.startedAtMillis = startedAtMillis;
        this.completesAtMillis = completesAtMillis;
        this.reviverStartLocation = reviverStartLocation.clone();
    }

    public UUID targetId() {
        return targetId;
    }

    public UUID reviverId() {
        return reviverId;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public long completesAtMillis() {
        return completesAtMillis;
    }

    public Location reviverStartLocation() {
        return reviverStartLocation.clone();
    }

    public double progress(long now) {
        long duration = Math.max(1L, completesAtMillis - startedAtMillis);
        return Math.max(0.0D, Math.min(1.0D, (double) (now - startedAtMillis) / (double) duration));
    }
}
