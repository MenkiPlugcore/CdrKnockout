package dev.cadera.cdrknockout.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired after a KNOCKED player returns to the normal living state. */
public final class CdrRevivedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final long knockoutStartedAtMillis;
    private final long knockoutDurationMillis;

    public CdrRevivedEvent(Player player, long knockoutStartedAtMillis, long knockoutDurationMillis) {
        super(player);
        this.knockoutStartedAtMillis = knockoutStartedAtMillis;
        this.knockoutDurationMillis = Math.max(0L, knockoutDurationMillis);
    }

    public long getKnockoutStartedAtMillis() {
        return knockoutStartedAtMillis;
    }

    public long getKnockoutDurationMillis() {
        return knockoutDurationMillis;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
