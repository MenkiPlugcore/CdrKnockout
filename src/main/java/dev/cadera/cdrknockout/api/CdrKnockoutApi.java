package dev.cadera.cdrknockout.api;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Optional;

/**
 * Public service API for CdrKnockout.
 *
 * <p>Other plugins should obtain this service through {@link CdrKnockoutProvider}
 * or Bukkit's ServicesManager rather than depending on implementation classes.</p>
 */
public interface CdrKnockoutApi {

    String getVersion();

    boolean isKnocked(Player player);

    boolean isDeathInProgress(Player player);

    long getRemainingSeconds(Player player);

    Optional<KnockoutSnapshot> getSnapshot(Player player);

    boolean knockout(Player player);

    boolean knockout(Player player, EntityDamageEvent.DamageCause cause, boolean force);

    boolean revive(Player player);

    boolean revive(Player player, double health, int resistanceSeconds);

    boolean forceDeath(Player player);

    boolean giveUp(Player player);

    boolean isBeingRevived(Player player);

    boolean isReviver(Player player);

    boolean isBeingExecuted(Player player);

    boolean isExecutor(Player player);

    String getClientPlatform(Player player);

    String getPoseMode(Player player);
}
