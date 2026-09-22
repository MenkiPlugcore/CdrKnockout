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
import org.bukkit.event.player.PlayerTeleportEvent;
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
    private final KnockoutPersistence persistence;
    private final Map<UUID, KnockoutSession> sessions = new HashMap<>();
    private final Set<UUID> deathInProgress = new HashSet<>();
    private final Set<UUID> internalTeleports = new HashSet<>();
    private ReviveManager reviveManager;
    private BukkitTask ticker;

    public KnockoutManager(
            CdrKnockoutPlugin plugin,
            Messages messages,
            KnockoutPersistence persistence
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.persistence = persistence;
    }

    public void setReviveManager(ReviveManager reviveManager) {
        this.reviveManager = reviveManager;
    }

    public void start() {
        if (ticker != null) {
            ticker.cancel();
        }
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 5L);

        long delay = Math.max(1L, plugin.getConfig().getLong(
                "stability.persistence.join-recovery-delay-ticks",
                2L
        ));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                recoverPlayer(player);
            }
        }, delay);
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }

        long now = System.currentTimeMillis();
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            KnockoutSession session = sessions.get(uuid);
            if (session == null) {
                continue;
            }
            Player player = plugin.getServer().getPlayer(uuid);
            session.markOffline(now);
            persistence.track(session);
            if (player != null) {
                restorePlayer(player, session);
            }
        }

        sessions.clear();
        deathInProgress.clear();
        internalTeleports.clear();
        persistence.flushNow();
    }

    public void onReload() {
        if (persistence.enabled()) {
            for (KnockoutSession session : sessions.values()) {
                persistence.track(session);
            }
        }
        debug("Runtime configuration reloaded. Active sessions=" + sessions.size()
                + ", persisted=" + persistence.storedCount());
    }

    public boolean isKnocked(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    public boolean isDeathInProgress(Player player) {
        return player != null && deathInProgress.contains(player.getUniqueId());
    }

    public boolean hasPersistedKnockout(Player player) {
        return player != null && persistence.has(player.getUniqueId());
    }

    public KnockoutSession getSession(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public long getRemainingSeconds(Player player) {
        KnockoutSession session = getSession(player);
        return session == null ? 0L : session.remainingSeconds(System.currentTimeMillis());
    }

    public void ensureRecovered(Player player) {
        if (player != null && !isKnocked(player) && persistence.has(player.getUniqueId())) {
            recoverPlayer(player);
        }
    }

    public boolean recoverPlayer(Player player) {
        if (player == null || !player.isOnline() || isKnocked(player) || !persistence.enabled()) {
            return false;
        }

        KnockoutPersistence.StoredSession stored = persistence.get(player.getUniqueId());
        if (stored == null) {
            return false;
        }

        Location anchor = resolveRecoveryAnchor(player, stored);
        if (anchor == null) {
            persistence.remove(player.getUniqueId());
            persistence.flushNow();
            player.sendMessage(messages.format("recovery-cleared-missing-world"));
            plugin.getLogger().warning("Cleared persisted knockout for " + player.getName()
                    + " because recovery world '" + stored.worldName() + "' is unavailable.");
            return false;
        }

        KnockoutSession session = stored.toSession(anchor);
        long now = System.currentTimeMillis();
        boolean offlineTimeCounts = plugin.getConfig().getBoolean(
                "stability.persistence.offline-time-counts",
                true
        );
        session.resume(now, offlineTimeCounts);
        sessions.put(player.getUniqueId(), session);
        deathInProgress.remove(player.getUniqueId());

        if (plugin.getConfig().getBoolean("stability.persistence.restore-anchor-on-join", true)) {
            safeTeleport(player, anchor);
        } else {
            session.updateAnchor(player.getLocation());
        }

        applyRecoveredState(player, session);
        persistence.track(session);
        persistence.flushNow();

        if (session.expiresAtMillis() != Long.MAX_VALUE && now >= session.expiresAtMillis()) {
            player.sendMessage(messages.format("recovery-expired"));
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> queueRealDeath(player, "player-bleedout", "RECOVERY_EXPIRED"));
        } else {
            player.sendMessage(messages.format(
                    "recovery-restored",
                    "%time%",
                    session.remainingSeconds(now) == Long.MAX_VALUE
                            ? "∞"
                            : Long.toString(session.remainingSeconds(now))
            ));
        }

        debug("Recovered persisted knockout: " + player.getName()
                + " remaining=" + session.remainingSeconds(now));
        return true;
    }

    public boolean shouldInterceptLethalDamage(Player player, EntityDamageEvent event) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("knockout.enabled", true)) {
            return false;
        }
        if (isKnocked(player) || isDeathInProgress(player) || player.isDead()) {
            return false;
        }
        if (persistence.has(player.getUniqueId())) {
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
        if (player == null || isKnocked(player) || isDeathInProgress(player)
                || persistence.has(player.getUniqueId())) {
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

        KnockoutSession previous = sessions.putIfAbsent(player.getUniqueId(), session);
        if (previous != null) {
            restorePlayer(player, session);
            return false;
        }

        persistence.track(session);
        persistence.flushNow();

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

    public void handleDownedDamage(Player player, EntityDamageEvent event) {
        event.setCancelled(true);
        KnockoutSession session = sessions.get(player.getUniqueId());
        if (session == null || deathInProgress.contains(player.getUniqueId())) {
            return;
        }

        DownedDamageMode mode = resolveDownedDamageMode(event.getCause());
        switch (mode) {
            case IGNORE -> debug("Downed damage ignored: " + player.getName() + " cause=" + event.getCause());
            case INSTANT_DEATH -> queueRealDeath(player, "player-downed-fatal", "DOWNED_" + event.getCause().name());
            case REDUCE_TIMER -> {
                long seconds = resolveDownedDamageReduction(event.getCause());
                if (seconds <= 0L || session.expiresAtMillis() == Long.MAX_VALUE) {
                    return;
                }
                long remaining = session.reduceRemainingMillis(seconds * 1000L, System.currentTimeMillis());
                persistence.track(session);

                if (plugin.getConfig().getBoolean("knockout.bleedout.downed-damage.display-penalty-actionbar", true)) {
                    String text = plugin.getConfig().getString(
                            "knockout.bleedout.downed-damage.penalty-actionbar",
                            "&4-%seconds%s &7bleedout &8| &c%time%s left"
                    );
                    if (text != null && !text.isBlank()) {
                        text = text.replace("%seconds%", Long.toString(seconds))
                                .replace("%time%", Long.toString(remaining));
                        player.sendActionBar(LEGACY.deserialize(text));
                    }
                }

                debug("Downed damage reduced timer: " + player.getName() + " cause=" + event.getCause()
                        + " seconds=" + seconds + " remaining=" + remaining);
                if (remaining <= 0L) {
                    queueRealDeath(player, "player-bleedout", "DOWNED_DAMAGE_TIMEOUT");
                }
            }
        }
    }

    public boolean revive(Player player) {
        double configuredHealth = Math.max(0.5D, plugin.getConfig().getDouble("admin-revive.health", 6.0D));
        int resistanceSeconds = Math.max(0, plugin.getConfig().getInt("admin-revive.resistance-seconds", 3));
        return revive(player, configuredHealth, resistanceSeconds);
    }

    public boolean revive(Player player, double health, int resistanceSeconds) {
        if (player == null || deathInProgress.contains(player.getUniqueId())) {
            return false;
        }
        if (reviveManager != null) {
            reviveManager.cancelTarget(player, false);
        }

        KnockoutSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }

        persistence.remove(player.getUniqueId());
        persistence.flushNow();

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
        return queueRealDeath(player, "player-bleedout", "ADMIN_FORCE_DEATH");
    }

    public boolean giveUp(Player player) {
        if (!plugin.getConfig().getBoolean("knockout.bleedout.giveup.enabled", true)) {
            return false;
        }
        return queueRealDeath(player, "player-giveup", "GIVEUP");
    }

    private boolean queueRealDeath(Player player, String messageKey, String reason) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (!sessions.containsKey(uuid) || !deathInProgress.add(uuid)) {
            return false;
        }

        if (reviveManager != null) {
            reviveManager.cancelTarget(player, false);
        }

        debug("Queue real death: " + player.getName() + " reason=" + reason);
        plugin.getServer().getScheduler().runTask(plugin, () -> executeRealDeath(uuid, messageKey, reason));
        return true;
    }

    private void executeRealDeath(UUID uuid, String messageKey, String reason) {
        Player player = plugin.getServer().getPlayer(uuid);
        KnockoutSession session = sessions.remove(uuid);
        if (player == null || !player.isOnline() || session == null) {
            deathInProgress.remove(uuid);
            if (session != null) {
                session.markOffline(System.currentTimeMillis());
                persistence.track(session);
                persistence.flushNow();
            }
            return;
        }

        restorePlayer(player, session);
        persistence.remove(uuid);
        persistence.flushNow();

        if (messageKey != null && !messageKey.isBlank()) {
            player.sendMessage(messages.format(messageKey));
        }
        debug("Real death pass-through: " + player.getName() + " reason=" + reason);

        if (!player.isDead()) {
            player.setHealth(0.0D);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> deathInProgress.remove(uuid), 2L);
    }

    public void cleanupExternalDeath(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        deathInProgress.remove(uuid);
        if (reviveManager != null) {
            reviveManager.cancelTarget(player, false);
        }
        KnockoutSession session = sessions.remove(uuid);
        if (session != null) {
            restorePlayer(player, session);
        }
        persistence.remove(uuid);
    }

    public void cleanupQuit(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        deathInProgress.remove(uuid);
        if (reviveManager != null) {
            reviveManager.cancelTarget(player, false);
        }

        KnockoutSession session = sessions.remove(uuid);
        if (session == null) {
            return;
        }

        restorePlayer(player, session);

        if (persistence.enabled()) {
            session.markOffline(System.currentTimeMillis());
            persistence.track(session);
            persistence.flushNow();
            debug("Persisted KNOCKED session on quit for " + player.getName());
        } else {
            persistence.remove(uuid);
            debug("Session cleared on quit for " + player.getName() + " because persistence is disabled.");
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

    public boolean isInternalTeleport(Player player) {
        return player != null && internalTeleports.contains(player.getUniqueId());
    }

    public boolean handleTeleportAttempt(
            Player player,
            Location destination,
            PlayerTeleportEvent.TeleportCause cause
    ) {
        if (player == null || destination == null || !isKnocked(player) || isInternalTeleport(player)) {
            return false;
        }

        KnockoutSession session = getSession(player);
        if (session == null) {
            return false;
        }

        if (!restriction("teleport", true)) {
            session.updateAnchor(destination);
            persistence.track(session);
            return false;
        }

        String mode = plugin.getConfig().getString("stability.teleport.mode", "BLOCK");
        if (mode != null && mode.equalsIgnoreCase("FOLLOW")) {
            session.updateAnchor(destination);
            persistence.track(session);
            debug("Following allowed teleport for KNOCKED player " + player.getName()
                    + " cause=" + cause);
            return false;
        }

        debug("Blocked teleport for KNOCKED player " + player.getName() + " cause=" + cause);
        return true;
    }

    public void handleUnexpectedWorldChange(Player player) {
        if (player == null || !isKnocked(player)) {
            return;
        }

        KnockoutSession session = getSession(player);
        if (session == null) {
            return;
        }

        Location anchor = session.anchor();
        if (anchor.getWorld() == null || anchor.getWorld().equals(player.getWorld())) {
            return;
        }

        String mode = plugin.getConfig().getString("stability.teleport.mode", "BLOCK");
        if (!restriction("teleport", true) || (mode != null && mode.equalsIgnoreCase("FOLLOW"))) {
            session.updateAnchor(player.getLocation());
            persistence.track(session);
            return;
        }

        if (!plugin.getConfig().getBoolean("stability.teleport.reassert-on-world-change", true)) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && isKnocked(player)) {
                safeTeleport(player, session.anchor());
                player.sendMessage(messages.format("teleport-blocked"));
            }
        });
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            KnockoutSession session = sessions.get(uuid);
            if (session == null || deathInProgress.contains(uuid)) {
                continue;
            }

            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                sessions.remove(uuid);
                session.markOffline(now);
                persistence.track(session);
                continue;
            }
            if (player.isDead()) {
                cleanupExternalDeath(player);
                continue;
            }

            if (session.expiresAtMillis() != Long.MAX_VALUE && now >= session.expiresAtMillis()) {
                queueRealDeath(player, "player-bleedout", "BLEEDOUT");
                continue;
            }

            enforcePose(player);
            if (plugin.getConfig().getBoolean("knockout.movement.zero-velocity", true)) {
                player.setVelocity(new Vector(0, 0, 0));
            }

            long remaining = session.remainingSeconds(now);
            processBleedoutFeedback(player, session, remaining, now);

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

    private void processBleedoutFeedback(Player player, KnockoutSession session, long remaining, long now) {
        if (remaining == Long.MAX_VALUE || remaining <= 0L) {
            return;
        }

        if (plugin.getConfig().getBoolean("knockout.bleedout.warnings.enabled", true)) {
            List<Integer> thresholds = plugin.getConfig().getIntegerList("knockout.bleedout.warnings.thresholds-seconds");
            if (thresholds.isEmpty()) {
                thresholds = List.of(30, 10, 5);
            }
            for (int threshold : thresholds) {
                if (threshold > 0 && remaining == threshold && session.markWarningSent(threshold)) {
                    sendBleedoutWarning(player, threshold);
                }
            }
        }

        if (plugin.getConfig().getBoolean("knockout.bleedout.heartbeat.enabled", true)) {
            long criticalAt = Math.max(1L, plugin.getConfig().getLong(
                    "knockout.bleedout.heartbeat.start-at-seconds",
                    10L
            ));
            long interval = Math.max(200L, plugin.getConfig().getLong(
                    "knockout.bleedout.heartbeat.interval-ms",
                    1000L
            ));
            if (remaining <= criticalAt && session.shouldHeartbeat(now, interval)) {
                playConfiguredSound(
                        player,
                        "knockout.bleedout.heartbeat.sound",
                        "minecraft:entity.warden.heartbeat",
                        "knockout.bleedout.heartbeat.volume",
                        0.8F,
                        "knockout.bleedout.heartbeat.pitch",
                        1.0F
                );
            }
        }
    }

    private void sendBleedoutWarning(Player player, int seconds) {
        if (plugin.getConfig().getBoolean("knockout.bleedout.warnings.chat-enabled", true)) {
            player.sendMessage(messages.format("bleedout-warning", "%time%", Integer.toString(seconds)));
        }

        if (plugin.getConfig().getBoolean("knockout.bleedout.warnings.title.enabled", true)) {
            String title = messages.color(plugin.getConfig().getString(
                    "knockout.bleedout.warnings.title.title",
                    "&c&lBLEEDING OUT"
            ));
            String subtitle = messages.color(plugin.getConfig().getString(
                    "knockout.bleedout.warnings.title.subtitle",
                    "&f%time%s remaining"
            )).replace("%time%", Integer.toString(seconds));
            player.sendTitle(title, subtitle, 2, 20, 5);
        }

        playConfiguredSound(
                player,
                "knockout.bleedout.warnings.sound",
                "minecraft:block.note_block.bass",
                "knockout.bleedout.warnings.volume",
                0.9F,
                "knockout.bleedout.warnings.pitch",
                0.7F
        );
    }

    private void playConfiguredSound(
            Player player,
            String soundPath,
            String fallbackSound,
            String volumePath,
            float fallbackVolume,
            String pitchPath,
            float fallbackPitch
    ) {
        String sound = plugin.getConfig().getString(soundPath, fallbackSound);
        if (sound == null || sound.isBlank()) {
            return;
        }
        float volume = (float) Math.max(0.0D, plugin.getConfig().getDouble(volumePath, fallbackVolume));
        float pitch = (float) Math.max(0.01D, plugin.getConfig().getDouble(pitchPath, fallbackPitch));
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private DownedDamageMode resolveDownedDamageMode(EntityDamageEvent.DamageCause cause) {
        String base = "knockout.bleedout.downed-damage.cause-overrides." + cause.name();
        String configured = plugin.getConfig().getString(base + ".mode");
        if (configured == null || configured.isBlank()) {
            configured = plugin.getConfig().getString(
                    "knockout.bleedout.downed-damage.default-mode",
                    "REDUCE_TIMER"
            );
        }
        try {
            return DownedDamageMode.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return DownedDamageMode.REDUCE_TIMER;
        }
    }

    private long resolveDownedDamageReduction(EntityDamageEvent.DamageCause cause) {
        String base = "knockout.bleedout.downed-damage.cause-overrides." + cause.name();
        if (plugin.getConfig().contains(base + ".reduce-seconds")) {
            return Math.max(0L, plugin.getConfig().getLong(base + ".reduce-seconds"));
        }
        return Math.max(0L, plugin.getConfig().getLong(
                "knockout.bleedout.downed-damage.default-reduce-seconds",
                4L
        ));
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

    private void applyRecoveredState(Player player, KnockoutSession session) {
        for (PotionEffectType type : session.managedEffects()) {
            player.removePotionEffect(type);
            String configName = configName(type);
            if (configName == null) {
                continue;
            }
            String base = "knockout.effects." + configName;
            if (!plugin.getConfig().getBoolean(base + ".enabled", true)) {
                continue;
            }
            int amplifier = Math.max(0, plugin.getConfig().getInt(base + ".amplifier", 0));
            player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amplifier, false, false, true), true);
        }

        double minimumHealth = Math.max(0.5D, plugin.getConfig().getDouble("knockout.minimum-health", 1.0D));
        player.setHealth(Math.min(player.getMaxHealth(), minimumHealth));
        player.setAbsorptionAmount(0.0D);
        player.closeInventory();
        player.setSprinting(false);
        player.setGliding(false);
        player.setVelocity(new Vector(0, 0, 0));
        enforcePose(player);
    }

    private String configName(PotionEffectType type) {
        if (type.equals(PotionEffectType.BLINDNESS)) {
            return "blindness";
        }
        if (type.equals(PotionEffectType.WEAKNESS)) {
            return "weakness";
        }
        if (type.equals(PotionEffectType.SLOWNESS)) {
            return "slowness";
        }
        if (type.equals(PotionEffectType.DARKNESS)) {
            return "darkness";
        }
        return null;
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

    private Location resolveRecoveryAnchor(Player player, KnockoutPersistence.StoredSession stored) {
        World world = stored.worldName().isBlank() ? null : plugin.getServer().getWorld(stored.worldName());
        if (world != null) {
            return new Location(
                    world,
                    stored.x(),
                    stored.y(),
                    stored.z(),
                    stored.yaw(),
                    stored.pitch()
            );
        }

        String policy = plugin.getConfig().getString(
                "stability.persistence.missing-world-policy",
                "CURRENT"
        );
        if (policy != null && policy.equalsIgnoreCase("CLEAR")) {
            return null;
        }

        return player.getLocation();
    }

    private void safeTeleport(Player player, Location destination) {
        if (destination == null || destination.getWorld() == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        internalTeleports.add(uuid);
        try {
            player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            internalTeleports.remove(uuid);
        }
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

    private enum DownedDamageMode {
        IGNORE,
        REDUCE_TIMER,
        INSTANT_DEATH
    }
}
