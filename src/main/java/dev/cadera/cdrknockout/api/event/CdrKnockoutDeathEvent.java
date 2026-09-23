package dev.cadera.cdrknockout.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired when a player who was KNOCKED reaches an actual Paper death. */
public final class CdrKnockoutDeathEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean managedDeath;
    private final long knockoutStartedAtMillis;
    private final long knockoutDurationMillis;

    public CdrKnockoutDeathEvent(
            Player player,
            boolean managedDeath,
            long knockoutStartedAtMillis,
            long knockoutDurationMillis
    ) {
        super(player);
        this.managedDeath = managedDeath;
        this.knockoutStartedAtMillis = knockoutStartedAtMillis;
        this.knockoutDurationMillis = Math.max(0L, knockoutDurationMillis);
    }

    public boolean isManagedDeath() {
        return managedDeath;
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
