package dev.cadera.cdrknockout.execution;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.UUID;

public final class ExecutionSession {

    private final UUID targetId;
    private final UUID executorId;
    private final long startedAtMillis;
    private final long completesAtMillis;
    private final Location executorStartLocation;
    private final Material lockedMaterial;

    public ExecutionSession(
            UUID targetId,
            UUID executorId,
            long startedAtMillis,
            long completesAtMillis,
            Location executorStartLocation,
            Material lockedMaterial
    ) {
        this.targetId = targetId;
        this.executorId = executorId;
        this.startedAtMillis = startedAtMillis;
        this.completesAtMillis = completesAtMillis;
        this.executorStartLocation = executorStartLocation.clone();
        this.lockedMaterial = lockedMaterial;
    }

    public UUID targetId() {
        return targetId;
    }

    public UUID executorId() {
        return executorId;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public long completesAtMillis() {
        return completesAtMillis;
    }

    public Location executorStartLocation() {
        return executorStartLocation.clone();
    }

    public Material lockedMaterial() {
        return lockedMaterial;
    }

    public double progress(long now) {
        long duration = Math.max(1L, completesAtMillis - startedAtMillis);
        long elapsed = Math.max(0L, Math.min(duration, now - startedAtMillis));
        return Math.max(0.0D, Math.min(1.0D, (double) elapsed / (double) duration));
    }
}
