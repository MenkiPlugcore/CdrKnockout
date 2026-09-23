package dev.cadera.cdrknockout.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired after a player has entered the CdrKnockout KNOCKED state. */
public final class CdrKnockoutEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final EntityDamageEvent.DamageCause damageCause;
    private final boolean recovered;
    private final long remainingSeconds;

    public CdrKnockoutEvent(
            Player player,
            EntityDamageEvent.DamageCause damageCause,
            boolean recovered,
            long remainingSeconds
    ) {
        super(player);
        this.damageCause = damageCause == null ? EntityDamageEvent.DamageCause.CUSTOM : damageCause;
        this.recovered = recovered;
        this.remainingSeconds = remainingSeconds;
    }

    public EntityDamageEvent.DamageCause getDamageCause() {
        return damageCause;
    }

    public boolean isRecovered() {
        return recovered;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
