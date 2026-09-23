package dev.cadera.cdrknockout.gameplay;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.api.event.CdrKnockoutDeathEvent;
import dev.cadera.cdrknockout.api.event.CdrKnockoutEvent;
import dev.cadera.cdrknockout.api.event.CdrRevivedEvent;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.execution.ExecutionManager;
import dev.cadera.cdrknockout.revive.ReviveManager;
import dev.cadera.cdrknockout.util.Messages;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SelfReviveManager implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final CdrKnockoutPlugin plugin;
    private final KnockoutManager knockoutManager;
    private final ReviveManager reviveManager;
    private final ExecutionManager executionManager;
    private final StatisticsManager statistics;
    private final Messages messages;
    private final Map<UUID, SelfReviveSession> sessions = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> retryBlockedUntil = new HashMap<>();
    private final Set<UUID> usedThisKnockout = new HashSet<>();
    private BukkitTask ticker;

    public SelfReviveManager(
            CdrKnockoutPlugin plugin,
            KnockoutManager knockoutManager,
            ReviveManager reviveManager,
            ExecutionManager executionManager,
            StatisticsManager statistics,
            Messages messages
    ) {
        this.plugin = plugin;
        this.knockoutManager = knockoutManager;
        this.reviveManager = reviveManager;
        this.executionManager = executionManager;
        this.statistics = statistics;
        this.messages = messages;
    }

    public void start() {
        shutdownTask();
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    public void reload() {
        cancelAll(false);
    }

    public void shutdown() {
        shutdownTask();
        cancelAll(false);
        cooldownUntil.clear();
        retryBlockedUntil.clear();
        usedThisKnockout.clear();
    }

    public boolean isSelfReviving(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    public boolean manualStart(Player player) {
        return tryStart(player, true);
    }

    public long cooldownRemainingSeconds(Player player) {
        if (player == null) {
            return 0L;
        }
        long remaining = Math.max(0L, cooldownUntil.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis());
        return (remaining + 999L) / 1000L;
    }

    @EventHandler
    public void onKnockout(CdrKnockoutEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        usedThisKnockout.remove(uuid);
        retryBlockedUntil.remove(uuid);
    }

    @EventHandler
    public void onRevived(CdrRevivedEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        usedThisKnockout.remove(uuid);
        retryBlockedUntil.remove(uuid);
    }

    @EventHandler
    public void onKnockoutDeath(CdrKnockoutDeathEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        usedThisKnockout.remove(uuid);
        retryBlockedUntil.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!sessions.containsKey(player.getUniqueId())) {
            return;
        }
        if (plugin.getConfig().getBoolean("gameplay.self-revive.cancel-on-damage", true)) {
            cancel(player, true);
        }
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("gameplay.self-revive.enabled", true)) {
            cancelAll(false);
            return;
        }

        long now = System.currentTimeMillis();
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            SelfReviveSession session = sessions.get(uuid);
            Player player = plugin.getServer().getPlayer(uuid);
            if (!validate(session, player)) {
                cancel(player, true);
                continue;
            }
            if (now >= session.completesAtMillis()) {
                complete(player);
                continue;
            }
            sendProgress(player, session, now);
        }

        if (!plugin.getConfig().getBoolean("gameplay.self-revive.auto-start", true)) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!sessions.containsKey(player.getUniqueId()) && matchesItem(player)) {
                tryStart(player, false);
            }
        }
    }

    private boolean tryStart(Player player, boolean notifyFailure) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("gameplay.self-revive.enabled", true)) {
            if (notifyFailure) {
                player.sendMessage(messages.format("self-revive-disabled"));
            }
            return false;
        }
        if (!knockoutManager.isKnocked(player) || knockoutManager.isDeathInProgress(player)) {
            if (notifyFailure) {
                player.sendMessage(messages.format("self-revive-not-knocked"));
            }
            return false;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            return true;
        }
        if (reviveManager.isTargetBeingRevived(player) || executionManager.isTargetBeingExecuted(player)) {
            if (notifyFailure) {
                player.sendMessage(messages.format("self-revive-busy"));
            }
            return false;
        }
        if (usedThisKnockout.contains(player.getUniqueId())) {
            if (notifyFailure) {
                player.sendMessage(messages.format("self-revive-used"));
            }
            return false;
        }
        long now = System.currentTimeMillis();
        long retry = retryBlockedUntil.getOrDefault(player.getUniqueId(), 0L);
        if (retry > now) {
            return false;
        }
        long cooldown = cooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (cooldown > now) {
            if (notifyFailure) {
                player.sendMessage(messages.format("self-revive-cooldown", "%time%", Long.toString((cooldown - now + 999L) / 1000L)));
            }
            return false;
        }
        if (!matchesItem(player)) {
            if (notifyFailure) {
                player.sendMessage(messages.format(
                        "self-revive-item",
                        "%amount%", Integer.toString(requiredAmount()),
                        "%item%", requiredMaterial().name()
                ));
            }
            return false;
        }

        double duration = Math.max(0.5D, plugin.getConfig().getDouble("gameplay.self-revive.duration-seconds", 10.0D));
        SelfReviveSession session = new SelfReviveSession(now, now + Math.max(1L, Math.round(duration * 1000.0D)), requiredMaterial());
        sessions.put(player.getUniqueId(), session);
        player.sendMessage(messages.format("self-revive-start"));
        sendProgress(player, session, now);
        return true;
    }

    private boolean validate(SelfReviveSession session, Player player) {
        if (session == null || player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (!knockoutManager.isKnocked(player) || knockoutManager.isDeathInProgress(player)) {
            return false;
        }
        if (reviveManager.isTargetBeingRevived(player) || executionManager.isTargetBeingExecuted(player)) {
            return false;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        return hand.getType() == session.lockedMaterial() && hand.getAmount() >= requiredAmount();
    }

    private void complete(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        sessions.remove(uuid);
        if (!matchesItem(player)) {
            cancel(player, true);
            return;
        }

        double health = Math.max(0.5D, plugin.getConfig().getDouble("gameplay.self-revive.result.health", 4.0D));
        int resistance = Math.max(0, plugin.getConfig().getInt("gameplay.self-revive.result.resistance-seconds", 3));
        if (!knockoutManager.revive(player, health, resistance)) {
            player.sendMessage(messages.format("self-revive-failed"));
            return;
        }

        consumeItem(player);
        usedThisKnockout.add(uuid);
        long cooldownSeconds = Math.max(0L, plugin.getConfig().getLong("gameplay.self-revive.cooldown-seconds", 120L));
        if (cooldownSeconds > 0L) {
            cooldownUntil.put(uuid, System.currentTimeMillis() + cooldownSeconds * 1000L);
        }
        statistics.recordSelfRevive(player);
        player.sendMessage(messages.format("self-revive-success"));
        player.sendActionBar(LEGACY.deserialize(plugin.getConfig().getString(
                "gameplay.self-revive.actionbar.success",
                "&a&lSELF REVIVE SUCCESS"
        )));
    }

    private void cancel(Player player, boolean notify) {
        if (player == null) {
            return;
        }
        if (sessions.remove(player.getUniqueId()) == null) {
            return;
        }
        long delay = Math.max(0L, plugin.getConfig().getLong("gameplay.self-revive.restart-delay-ms", 1500L));
        retryBlockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + delay);
        if (notify && player.isOnline() && knockoutManager.isKnocked(player)) {
            player.sendMessage(messages.format("self-revive-cancelled"));
            player.sendActionBar(LEGACY.deserialize(plugin.getConfig().getString(
                    "gameplay.self-revive.actionbar.cancelled",
                    "&c&lSELF REVIVE INTERRUPTED"
            )));
        }
    }

    private void cancelAll(boolean notify) {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                cancel(player, notify);
            } else {
                sessions.remove(uuid);
            }
        }
    }

    private void sendProgress(Player player, SelfReviveSession session, long now) {
        if (!plugin.getConfig().getBoolean("gameplay.self-revive.actionbar.enabled", true)) {
            return;
        }
        double progress = session.progress(now);
        int percent = (int) Math.floor(progress * 100.0D);
        int segments = Math.max(5, plugin.getConfig().getInt("gameplay.self-revive.actionbar.bar-segments", 10));
        int filled = Math.max(0, Math.min(segments, (int) Math.floor(progress * segments)));
        String bar = "&e" + "█".repeat(filled) + "&7" + "█".repeat(segments - filled);
        String text = plugin.getConfig().getString(
                "gameplay.self-revive.actionbar.progress",
                "&e✚ SELF REVIVING &8| %bar% &f%percent%%%"
        );
        if (text != null) {
            player.sendActionBar(LEGACY.deserialize(text
                    .replace("%bar%", bar)
                    .replace("%percent%", Integer.toString(percent))));
        }
    }

    private boolean matchesItem(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        return hand.getType() == requiredMaterial() && hand.getAmount() >= requiredAmount();
    }

    private Material requiredMaterial() {
        String configured = plugin.getConfig().getString("gameplay.self-revive.item.material", "ENCHANTED_GOLDEN_APPLE");
        Material material = configured == null ? null : Material.matchMaterial(configured);
        return material == null ? Material.ENCHANTED_GOLDEN_APPLE : material;
    }

    private int requiredAmount() {
        return Math.max(1, plugin.getConfig().getInt("gameplay.self-revive.item.amount", 1));
    }

    private void consumeItem(Player player) {
        if (!plugin.getConfig().getBoolean("gameplay.self-revive.item.consume-on-success", true)) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != requiredMaterial() || hand.getAmount() < requiredAmount()) {
            return;
        }
        int remaining = hand.getAmount() - requiredAmount();
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            hand.setAmount(remaining);
        }
    }

    private void shutdownTask() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private record SelfReviveSession(long startedAtMillis, long completesAtMillis, Material lockedMaterial) {
        private double progress(long now) {
            long duration = Math.max(1L, completesAtMillis - startedAtMillis);
            return Math.max(0.0D, Math.min(1.0D, (double) (now - startedAtMillis) / (double) duration));
        }
    }
}
