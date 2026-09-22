package dev.cadera.cdrknockout.core;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.revive.ReviveManager;
import dev.cadera.cdrknockout.util.Messages;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KnockoutManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final CdrKnockoutPlugin plugin;
    private final Messages messages;
    private final Map<UUID, KnockoutSession> sessions = new HashMap<>();
    private ReviveManager reviveManager;
    private BukkitTask ticker;

    public KnockoutManager(CdrKnockoutPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void setReviveManager(ReviveManager reviveManager) {
        this.reviveManager = reviveManager;
    }

    public void start() {
        if (ticker != null) {
            ticker.cancel();
        }
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 5L);
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                restorePlayer(player, sessions.get(uuid));
            }
        }
        sessions.clear();
    }

    public void onReload() {
        debug("Runtime configuration reloaded. Active sessions=" + sessions.size());
    }

    public boolean isKnocked(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public KnockoutSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public long getRemainingSeconds(Player player) {
        KnockoutSession session = getSession(player);
        return session == null ? 0L : session.remainingSeconds(System.currentTimeMillis());
    }

    public boolean shouldInterceptLethalDamage(Player player, EntityDamageEvent event) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("knockout.enabled", true)) {
            return false;
        }
        if (isKnocked(player) || player.isDead()) {
            return false;
        }
        if (player.hasPermission("cdrknockout.bypass")) {
            return false;
        }
        if (!isWorldEnabled(player.getWorld())) {
            return false;
        }
        if (isIgnoredGameMode(player.getGameMode())) {
            return false;
        }
        if (isIgnoredDamageCause(event.getCause())) {
            return false;
        }
        if (config.getBoolean("knockout.respect-totem-of-undying", true) && hasTotem(player)) {
            return false;
        }

        double effectiveLife = Math.max(0.0D, player.getHealth()) + Math.max(0.0D, player.getAbsorptionAmount());
        return event.getFinalDamage() + 1.0E-7D >= effectiveLife;
    }

    public boolean knockout(Player player, EntityDamageEvent.DamageCause cause, boolean force) {
        if (isKnocked(player)) {
            return false;
        }
        if (!force && !isWorldEnabled(player.getWorld())) {
            return false;
        }

        long now = System.currentTimeMillis();
        boolean bleedoutEnabled = plugin.getConfig().getBoolean("knockout.bleedout.enabled", true);
        long durationSeconds = Math.max(1L, plugin.getConfig().getLong("knockout.duration-seconds", 60L));
        long expiresAt = bleedoutEnabled ? now + (durationSeconds * 1000L) : Long.MAX_VALUE;

        Map<PotionEffectType, PotionEffect> previousEffects = new HashMap<>();
        Set<PotionEffectType> managedEffects = new HashSet<>();
        captureAndApplyEffect(player, previousEffects, managedEffects, PotionEffectType.BLINDNESS, "blindness");
        captureAndApplyEffect(player, previousEffects, managedEffects, PotionEffectType.WEAKNESS, "weakness");
        captureAndApplyEffect(player, previousEffects, managedEffects, PotionEffectType.SLOWNESS, "slowness");
        captureAndApplyEffect(player, previousEffects, managedEffects, PotionEffectType.DARKNESS, "darkness");

        KnockoutSession session = new KnockoutSession(
                player.getUniqueId(),
                now,
                expiresAt,
                player.getLocation(),
                player.isSwimming(),
                previousEffects,
                managedEffects
        );
        sessions.put(player.getUniqueId(), session);

        double minimumHealth = Math.max(0.5D, plugin.getConfig().getDouble("knockout.minimum-health", 1.0D));
        minimumHealth = Math.min(minimumHealth, player.getMaxHealth());
        player.setHealth(minimumHealth);
        player.setAbsorptionAmount(0.0D);
        player.closeInventory();
        player.setSprinting(false);
        player.setGliding(false);
        if (plugin.getConfig().getBoolean("knockout.movement.zero-velocity", true)) {
            player.setVelocity(new Vector(0, 0, 0));
        }
        enforcePose(player);

        if (plugin.getConfig().getBoolean("knockout.display.title.enabled", true)) {
            String title = messages.color(plugin.getConfig().getString("knockout.display.title.title", "&c&lKNOCKED"));
            String subtitle = messages.color(plugin.getConfig().getString("knockout.display.title.subtitle", "&7Wait for help..."));
            player.sendTitle(title, subtitle, 5, 35, 10);
        }
        player.sendMessage(messages.format("player-knocked"));

        debug("Knockout: " + player.getName() + " cause=" + (cause == null ? "MANUAL" : cause.name()));
        return true;
    }

    public boolean revive(Player player) {
        double configuredHealth = Math.max(0.5D, plugin.getConfig().getDouble("admin-revive.health", 6.0D));
        int resistanceSeconds = Math.max(0, plugin.getConfig().getInt("admin-revive.resistance-seconds", 3));
        return revive(player, configuredHealth, resistanceSeconds);
    }

    public boolean revive(Player player, double health, int resistanceSeconds) {
        if (reviveManager != null) {
            reviveManager.cancelTarget(player, false);
        }

        KnockoutSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }

        restorePlayer(player, session);
        player.setHealth(Math.min(player.getMaxHealth(), Math.max(0.5D, health)));

        if (resistanceSeconds > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, resistanceSeconds * 20, 1, false, false, true));
        }
        player.sendMessage(messages.format("player-revived"));
        debug("Revived: " + player.getName());
        return true;
    }

    public boolean forceDeath(Player player) {
        if (reviveManager != null) {
            reviveManager.cancelTarget(player, false);
        }

        KnockoutSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }

        restorePlayer(player, session);
        player.sendMessage(messages.format("player-bleedout"));
        debug("Real death pass-through: " + player.getName());
        player.setHealth(0.0D);
        return true;
    }

    public void cleanupExternalDeath(Player player) {
        if (reviveManager != null) {
            reviveManager.cancelTarget(player, false);
        }
        KnockoutSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            restorePlayer(player, session);
        }
    }

    public void cleanupQuit(Player player) {
        if (reviveManager != null) {
            reviveManager.cancelTarget(player, false);
        }
        KnockoutSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            restorePlayer(player, session);
            debug("Session cleared on quit for " + player.getName() + " (persistence arrives in v0.3.1).");
        }
    }

    public Location lockedLocation(Player player, float yaw, float pitch) {
        KnockoutSession session = getSession(player);
        if (session == null) {
            return null;
        }
        Location location = session.anchor();
        if (plugin.getConfig().getBoolean("knockout.movement.allow-camera", true)) {
            location.setYaw(yaw);
            location.setPitch(pitch);
        }
        return location;
    }

    public boolean isCommandAllowed(String label) {
        List<String> allowed = plugin.getConfig().getStringList("knockout.restrictions.commands.allowed");
        for (String entry : allowed) {
            if (entry.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    public boolean restriction(String path, boolean defaultValue) {
        return plugin.getConfig().getBoolean("knockout.restrictions." + path, defaultValue);
    }

    public boolean isWorldEnabled(World world) {
        String mode = plugin.getConfig().getString("knockout.worlds.mode", "ALL");
        mode = mode == null ? "ALL" : mode.toUpperCase(Locale.ROOT);
        List<String> configured = plugin.getConfig().getStringList("knockout.worlds.list");
        boolean listed = configured.stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
        return switch (mode) {
            case "WHITELIST" -> listed;
            case "BLACKLIST" -> !listed;
            default -> true;
        };
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            KnockoutSession session = sessions.get(uuid);
            if (session == null) {
                continue;
            }

            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                sessions.remove(uuid);
                continue;
            }
            if (player.isDead()) {
                cleanupExternalDeath(player);
                continue;
            }

            if (session.expiresAtMillis() != Long.MAX_VALUE && now >= session.expiresAtMillis()) {
                forceDeath(player);
                continue;
            }

            enforcePose(player);
            if (plugin.getConfig().getBoolean("knockout.movement.zero-velocity", true)) {
                player.setVelocity(new Vector(0, 0, 0));
            }

            long remaining = session.remainingSeconds(now);
            if (plugin.getConfig().getBoolean("knockout.display.actionbar.enabled", true)
                    && (reviveManager == null || !reviveManager.isTargetBeingRevived(player))
                    && session.shouldRefreshDisplay(remaining)) {
                String text = plugin.getConfig().getString(
                        "knockout.display.actionbar.text",
                        "&c&lKNOCKED &8| &fBleedout: &c%time%s"
                );
                if (text != null) {
                    text = text.replace("%time%", remaining == Long.MAX_VALUE ? "∞" : Long.toString(remaining));
                    player.sendActionBar(LEGACY.deserialize(text));
                }
            }
        }
    }

    private void enforcePose(Player player) {
        if (!plugin.getConfig().getBoolean("knockout.pose.enabled", true)) {
            return;
        }
        String mode = plugin.getConfig().getString("knockout.pose.mode", "SWIMMING");
        if (mode != null && mode.equalsIgnoreCase("SWIMMING") && !player.isSwimming()) {
            player.setSwimming(true);
        }
    }

    private void restorePlayer(Player player, KnockoutSession session) {
        for (PotionEffectType type : session.managedEffects()) {
            player.removePotionEffect(type);
            PotionEffect previous = session.previousEffects().get(type);
            if (previous != null) {
                player.addPotionEffect(previous);
            }
        }
        if (player.isOnline() && !player.isDead()) {
            player.setSwimming(session.originalSwimming());
            player.setVelocity(new Vector(0, 0, 0));
        }
    }

    private void captureAndApplyEffect(
            Player player,
            Map<PotionEffectType, PotionEffect> previous,
            Set<PotionEffectType> managed,
            PotionEffectType type,
            String configName
    ) {
        String base = "knockout.effects." + configName;
        if (!plugin.getConfig().getBoolean(base + ".enabled", false)) {
            return;
        }
        managed.add(type);
        PotionEffect current = player.getPotionEffect(type);
        if (current != null) {
            previous.put(type, current);
        }
        int amplifier = Math.max(0, plugin.getConfig().getInt(base + ".amplifier", 0));
        player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amplifier, false, false, true), true);
    }

    private boolean isIgnoredGameMode(GameMode gameMode) {
        return plugin.getConfig().getStringList("knockout.ignored-gamemodes")
                .stream()
                .anyMatch(value -> value.equalsIgnoreCase(gameMode.name()));
    }

    private boolean isIgnoredDamageCause(EntityDamageEvent.DamageCause cause) {
        return plugin.getConfig().getStringList("knockout.ignored-damage-causes")
                .stream()
                .anyMatch(value -> value.equalsIgnoreCase(cause.name()));
    }

    private boolean hasTotem(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        return main.getType() == Material.TOTEM_OF_UNDYING || off.getType() == Material.TOTEM_OF_UNDYING;
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }
}
