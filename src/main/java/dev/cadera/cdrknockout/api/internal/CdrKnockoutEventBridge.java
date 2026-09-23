package dev.cadera.cdrknockout.api.internal;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.api.event.CdrKnockoutDeathEvent;
import dev.cadera.cdrknockout.api.event.CdrKnockoutEvent;
import dev.cadera.cdrknockout.api.event.CdrRevivedEvent;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.core.KnockoutSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Emits public post-transition events without coupling the core state engine to
 * downstream integrations. Damage-caused KOs are observed at MONITOR priority;
 * admin/API/recovery transitions are caught by the lightweight state poller.
 */
public final class CdrKnockoutEventBridge implements Listener {

    private final CdrKnockoutPlugin plugin;
    private final KnockoutManager knockoutManager;
    private final Map<UUID, ActiveTransition> active = new HashMap<>();
    private final Set<UUID> recoveryCandidates = new HashSet<>();
    private BukkitTask ticker;

    public CdrKnockoutEventBridge(CdrKnockoutPlugin plugin, KnockoutManager knockoutManager) {
        this.plugin = plugin;
        this.knockoutManager = knockoutManager;
    }

    public void start() {
        shutdown();
        if (!plugin.getConfig().getBoolean("api.events.enabled", true)) {
            return;
        }
        long ticks = Math.max(1L, plugin.getConfig().getLong("api.events.poll-ticks", 2L));
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::poll, ticks, ticks);
    }

    public void reload() {
        start();
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        active.clear();
        recoveryCandidates.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (knockoutManager.isKnocked(player) && !active.containsKey(player.getUniqueId())) {
            registerKnockout(player, event.getCause(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (knockoutManager.hasPersistedKnockout(player)) {
            recoveryCandidates.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ActiveTransition transition = active.remove(player.getUniqueId());
        if (transition == null) {
            return;
        }

        recoveryCandidates.remove(player.getUniqueId());
        plugin.getServer().getPluginManager().callEvent(new CdrKnockoutDeathEvent(
                player,
                knockoutManager.isDeathInProgress(player),
                transition.startedAtMillis(),
                System.currentTimeMillis() - transition.startedAtMillis()
        ));
    }

    private void poll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean knocked = knockoutManager.isKnocked(player);
            ActiveTransition transition = active.get(uuid);

            if (knocked && transition == null) {
                boolean recovered = recoveryCandidates.remove(uuid);
                registerKnockout(player, EntityDamageEvent.DamageCause.CUSTOM, recovered);
                continue;
            }

            if (!knocked && transition != null && !player.isDead()) {
                active.remove(uuid);
                recoveryCandidates.remove(uuid);
                plugin.getServer().getPluginManager().callEvent(new CdrRevivedEvent(
                        player,
                        transition.startedAtMillis(),
                        System.currentTimeMillis() - transition.startedAtMillis()
                ));
            }
        }
    }

    private void registerKnockout(Player player, EntityDamageEvent.DamageCause cause, boolean recovered) {
        UUID uuid = player.getUniqueId();
        if (active.containsKey(uuid)) {
            return;
        }

        KnockoutSession session = knockoutManager.getSession(player);
        long startedAt = session == null ? System.currentTimeMillis() : session.startedAtMillis();
        long remaining = knockoutManager.getRemainingSeconds(player);
        active.put(uuid, new ActiveTransition(startedAt));
        recoveryCandidates.remove(uuid);

        plugin.getServer().getPluginManager().callEvent(new CdrKnockoutEvent(
                player,
                cause,
                recovered,
                remaining
        ));
    }

    private record ActiveTransition(long startedAtMillis) {
    }
}
