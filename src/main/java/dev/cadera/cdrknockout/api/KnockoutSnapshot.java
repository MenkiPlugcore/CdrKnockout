package dev.cadera.cdrknockout.api;

import org.bukkit.Location;

import java.util.UUID;

/** Immutable read-only view of a knockout session. */
public record KnockoutSnapshot(
        UUID playerId,
        long startedAtMillis,
        long expiresAtMillis,
        long remainingSeconds,
        Location anchor
) {
    public KnockoutSnapshot {
        anchor = anchor == null ? null : anchor.clone();
    }

    @Override
    public Location anchor() {
        return anchor == null ? null : anchor.clone();
    }
}
