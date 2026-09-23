package dev.cadera.cdrknockout.listener;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.execution.ExecutionManager;
import dev.cadera.cdrknockout.revive.ReviveManager;
import dev.cadera.cdrknockout.util.Messages;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.util.Locale;
import java.util.Set;

public final class KnockoutListener implements Listener {

    private static final Set<String> BUILT_IN_KNOCKED_COMMANDS = Set.of(
            "giveup",
            "selfrevive",
            "distress",
            "kostats",
            "cdrko",
            "cdrknockout",
            "cko"
    );

    private final CdrKnockoutPlugin plugin;
    private final KnockoutManager manager;
    private final ReviveManager reviveManager;
    private final ExecutionManager executionManager;
    private final Messages messages;

    public KnockoutListener(
            CdrKnockoutPlugin plugin,
            KnockoutManager manager,
            ReviveManager reviveManager,
            ExecutionManager executionManager,
            Messages messages
    ) {
        this.plugin = plugin;
        this.manager = manager;
        this.reviveManager = reviveManager;
        this.executionManager = executionManager;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        manager.ensureRecovered(player);
        reviveManager.onPlayerDamaged(player);
        executionManager.onPlayerDamaged(player);

        if (manager.isKnocked(player)) {
            manager.handleDownedDamage(player, event);
            return;
        }

        if (manager.shouldInterceptLethalDamage(player, event)) {
            event.setCancelled(true);
            manager.knockout(player, event.getCause(), false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOutgoingDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            manager.ensureRecovered(player);
            reviveManager.onReviverAttack(player);
            executionManager.onExecutorAttack(player);
            if (manager.isKnocked(player) && manager.restriction("attack", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!manager.isKnocked(player)
                || !plugin.getConfig().getBoolean("knockout.movement.lock-position", true)
                || event.getTo() == null) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        boolean positionChanged = from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ();
        if (!positionChanged) {
            return;
        }

        Location locked = manager.lockedLocation(player, to.getYaw(), to.getPitch());
        if (locked != null) {
            event.setTo(locked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!manager.isKnocked(player) || manager.isInternalTeleport(player)) {
            return;
        }

        if (manager.handleTeleportAttempt(player, event.getTo(), event.getCause())) {
            event.setCancelled(true);
            player.sendMessage(messages.format("teleport-blocked"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        manager.handleUnexpectedWorldChange(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        long delay = Math.max(1L, plugin.getConfig().getLong(
                "stability.persistence.join-recovery-delay-ticks",
                2L
        ));
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> manager.recoverPlayer(event.getPlayer()),
                delay
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)
                && reviveManager.shouldBlockReviveItemUse(player)) {
            event.setCancelled(true);
        }

        if (manager.isKnocked(player)
                && (manager.restriction("interact", true) || manager.restriction("item-use", true))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (manager.isKnocked(event.getPlayer()) && manager.restriction("interact", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (reviveManager.shouldBlockReviveItemUse(event.getPlayer())
                && reviveManager.matchesRequiredItem(event.getItem())) {
            event.setCancelled(true);
            return;
        }
        if (manager.isKnocked(event.getPlayer()) && manager.restriction("item-use", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.isKnocked(event.getPlayer()) && manager.restriction("item-drop", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (manager.isKnocked(event.getPlayer()) && manager.restriction("item-use", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (manager.isKnocked(event.getPlayer()) && manager.restriction("interact", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (manager.isKnocked(event.getPlayer()) && manager.restriction("interact", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && manager.isKnocked(player)
                && manager.restriction("inventory", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && manager.isKnocked(player)
                && manager.restriction("inventory", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && manager.isKnocked(player)
                && manager.restriction("inventory", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (manager.isKnocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!manager.isKnocked(player)
                || !plugin.getConfig().getBoolean("knockout.restrictions.commands.enabled", true)) {
            return;
        }

        String raw = event.getMessage().trim();
        if (raw.startsWith("/")) {
            raw = raw.substring(1);
        }
        if (raw.isBlank()) {
            return;
        }
        String label = raw.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int namespaceSeparator = label.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < label.length()) {
            label = label.substring(namespaceSeparator + 1);
        }

        if (plugin.getConfig().getBoolean("production.always-allow-knocked-plugin-commands", true)
                && BUILT_IN_KNOCKED_COMMANDS.contains(label)) {
            return;
        }

        if (!manager.isCommandAllowed(label)) {
            event.setCancelled(true);
            player.sendMessage(messages.format("command-blocked"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        executionManager.handlePlayerUnavailable(event.getEntity());
        reviveManager.handlePlayerUnavailable(event.getEntity());
        manager.cleanupExternalDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        executionManager.handlePlayerUnavailable(event.getPlayer());
        reviveManager.handlePlayerUnavailable(event.getPlayer());
        manager.cleanupQuit(event.getPlayer());
    }
}
