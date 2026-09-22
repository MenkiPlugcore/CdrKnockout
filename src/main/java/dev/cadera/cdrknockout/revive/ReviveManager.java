package dev.cadera.cdrknockout.revive;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ReviveManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final CdrKnockoutPlugin plugin;
    private final KnockoutManager knockoutManager;
    private final Messages messages;
    private final Map<UUID, ReviveSession> sessionsByTarget = new HashMap<>();
    private final Map<UUID, UUID> targetByReviver = new HashMap<>();
    private BukkitTask ticker;

    public ReviveManager(CdrKnockoutPlugin plugin, KnockoutManager knockoutManager, Messages messages) {
        this.plugin = plugin;
        this.knockoutManager = knockoutManager;
        this.messages = messages;
    }

    public void start() {
        if (ticker != null) {
            ticker.cancel();
        }
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        cancelAll(false);
    }

    public void onReload() {
        cancelAll(true);
        debug("Revive configuration reloaded; active channels reset.");
    }

    public boolean isTargetBeingRevived(Player player) {
        return sessionsByTarget.containsKey(player.getUniqueId());
    }

    public boolean isReviver(Player player) {
        return targetByReviver.containsKey(player.getUniqueId());
    }

    public void cancelTarget(Player target, boolean notify) {
        cancelSession(target.getUniqueId(), notify);
    }

    public void handlePlayerUnavailable(Player player) {
        UUID targetId = targetByReviver.get(player.getUniqueId());
        if (targetId != null) {
            cancelSession(targetId, true);
        }
        cancelSession(player.getUniqueId(), false);
    }

    public void onPlayerDamaged(Player player) {
        if (!plugin.getConfig().getBoolean("revive.channel.cancel-on-damage", true)) {
            return;
        }
        UUID targetId = targetByReviver.get(player.getUniqueId());
        if (targetId != null) {
            cancelSession(targetId, true);
        }
    }

    public void onReviverAttack(Player player) {
        if (!plugin.getConfig().getBoolean("revive.channel.cancel-on-attack", true)) {
            return;
        }
        UUID targetId = targetByReviver.get(player.getUniqueId());
        if (targetId != null) {
            cancelSession(targetId, true);
        }
    }

    public boolean shouldBlockReviveItemUse(Player player) {
        if (!plugin.getConfig().getBoolean("revive.enabled", true)
                || !plugin.getConfig().getBoolean("revive.item.enabled", true)
                || !player.isSneaking()
                || !matchesRequiredItem(player.getInventory().getItemInMainHand())) {
            return false;
        }

        if (isReviver(player)) {
            return true;
        }

        double maxDistance = maxDistance();
        double maxDistanceSquared = maxDistance * maxDistance;
        for (Player target : player.getWorld().getPlayers()) {
            if (!knockoutManager.isKnocked(target) || sessionsByTarget.containsKey(target.getUniqueId())) {
                continue;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (target.getLocation().distanceSquared(player.getLocation()) <= maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesRequiredItem(ItemStack stack) {
        if (!plugin.getConfig().getBoolean("revive.item.enabled", true)) {
            return true;
        }
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return stack.getType() == requiredMaterial() && stack.getAmount() >= requiredAmount();
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("revive.enabled", true)) {
            if (!sessionsByTarget.isEmpty()) {
                cancelAll(false);
            }
            return;
        }

        long now = System.currentTimeMillis();

        for (UUID targetId : new ArrayList<>(sessionsByTarget.keySet())) {
            ReviveSession session = sessionsByTarget.get(targetId);
            if (session == null) {
                continue;
            }

            Player target = plugin.getServer().getPlayer(session.targetId());
            Player reviver = plugin.getServer().getPlayer(session.reviverId());
            if (!isSessionValid(session, target, reviver)) {
                cancelSession(targetId, true);
                continue;
            }

            if (now >= session.completesAtMillis()) {
                completeSession(session, target, reviver);
                continue;
            }

            sendProgress(session, target, reviver, now);
        }

        for (Player target : plugin.getServer().getOnlinePlayers()) {
            if (!knockoutManager.isKnocked(target) || sessionsByTarget.containsKey(target.getUniqueId())) {
                continue;
            }
            Player reviver = findEligibleReviver(target);
            if (reviver != null) {
                startSession(target, reviver, now);
            }
        }
    }

    private Player findEligibleReviver(Player target) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        double maxDistance = maxDistance();
        double maxDistanceSquared = maxDistance * maxDistance;

        for (Player candidate : target.getWorld().getPlayers()) {
            if (candidate.getUniqueId().equals(target.getUniqueId())
                    || targetByReviver.containsKey(candidate.getUniqueId())
                    || knockoutManager.isKnocked(candidate)
                    || candidate.isDead()
                    || !candidate.isOnline()
                    || !candidate.isSneaking()
                    || !matchesRequiredItem(candidate.getInventory().getItemInMainHand())) {
                continue;
            }

            double distance = candidate.getLocation().distanceSquared(target.getLocation());
            if (distance <= maxDistanceSquared && distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void startSession(Player target, Player reviver, long now) {
        double durationSeconds = Math.max(0.5D, plugin.getConfig().getDouble("revive.duration-seconds", 8.0D));
        long completesAt = now + Math.max(1L, Math.round(durationSeconds * 1000.0D));

        ReviveSession session = new ReviveSession(
                target.getUniqueId(),
                reviver.getUniqueId(),
                now,
                completesAt,
                reviver.getLocation()
        );
        sessionsByTarget.put(target.getUniqueId(), session);
        targetByReviver.put(reviver.getUniqueId(), target.getUniqueId());

        reviver.sendMessage(messages.format("revive-start-reviver", "%target%", target.getName()));
        target.sendMessage(messages.format("revive-start-target", "%reviver%", reviver.getName()));
        sendProgress(session, target, reviver, now);
        debug("Revive started: " + reviver.getName() + " -> " + target.getName());
    }

    private boolean isSessionValid(ReviveSession session, Player target, Player reviver) {
        if (target == null || reviver == null
                || !target.isOnline() || !reviver.isOnline()
                || target.isDead() || reviver.isDead()
                || !knockoutManager.isKnocked(target)
                || knockoutManager.isKnocked(reviver)
                || !target.getWorld().equals(reviver.getWorld())
                || !reviver.isSneaking()
                || !matchesRequiredItem(reviver.getInventory().getItemInMainHand())) {
            return false;
        }

        double maxDistance = maxDistance();
        if (target.getLocation().distanceSquared(reviver.getLocation()) > maxDistance * maxDistance) {
            return false;
        }

        if (plugin.getConfig().getBoolean("revive.channel.cancel-on-move", false)) {
            Location start = session.reviverStartLocation();
            Location current = reviver.getLocation();
            if (!start.getWorld().equals(current.getWorld())) {
                return false;
            }
            double tolerance = Math.max(0.0D, plugin.getConfig().getDouble("revive.channel.move-tolerance", 0.10D));
            if (start.distanceSquared(current) > tolerance * tolerance) {
                return false;
            }
        }
        return true;
    }

    private void completeSession(ReviveSession session, Player target, Player reviver) {
        removeSession(session);

        double health = Math.max(0.5D, plugin.getConfig().getDouble("revive.result.health", 6.0D));
        int resistanceSeconds = Math.max(0, plugin.getConfig().getInt("revive.result.resistance-seconds", 3));
        if (!knockoutManager.revive(target, health, resistanceSeconds)) {
            return;
        }

        if (plugin.getConfig().getBoolean("revive.item.enabled", true)
                && plugin.getConfig().getBoolean("revive.item.consume-on-success", true)) {
            consumeRequiredItem(reviver);
        }

        reviver.sendMessage(messages.format("revive-success-reviver", "%target%", target.getName()));
        sendConfiguredActionBar(reviver, "revive.display.actionbar.success-reviver", "&a&lREVIVE SUCCESS");
        sendConfiguredActionBar(target, "revive.display.actionbar.success-target", "&a&lREVIVED");
        debug("Revive completed: " + reviver.getName() + " -> " + target.getName());
    }

    private void cancelSession(UUID targetId, boolean notify) {
        ReviveSession session = sessionsByTarget.remove(targetId);
        if (session == null) {
            return;
        }
        targetByReviver.remove(session.reviverId(), session.targetId());

        Player target = plugin.getServer().getPlayer(session.targetId());
        Player reviver = plugin.getServer().getPlayer(session.reviverId());
        if (notify) {
            if (reviver != null && reviver.isOnline()) {
                reviver.sendMessage(messages.format("revive-cancelled-reviver"));
                sendConfiguredActionBar(reviver, "revive.display.actionbar.cancelled", "&c&lREVIVE INTERRUPTED");
            }
            if (target != null && target.isOnline() && knockoutManager.isKnocked(target)) {
                target.sendMessage(messages.format("revive-cancelled-target"));
            }
        }
        debug("Revive cancelled: target=" + session.targetId() + " reviver=" + session.reviverId());
    }

    private void cancelAll(boolean notify) {
        for (UUID targetId : new ArrayList<>(sessionsByTarget.keySet())) {
            cancelSession(targetId, notify);
        }
        sessionsByTarget.clear();
        targetByReviver.clear();
    }

    private void removeSession(ReviveSession session) {
        sessionsByTarget.remove(session.targetId());
        targetByReviver.remove(session.reviverId(), session.targetId());
    }

    private void consumeRequiredItem(Player reviver) {
        ItemStack stack = reviver.getInventory().getItemInMainHand();
        int amount = requiredAmount();
        if (!matchesRequiredItem(stack)) {
            return;
        }
        int remaining = stack.getAmount() - amount;
        if (remaining <= 0) {
            reviver.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            stack.setAmount(remaining);
        }
    }

    private void sendProgress(ReviveSession session, Player target, Player reviver, long now) {
        if (!plugin.getConfig().getBoolean("revive.display.actionbar.enabled", true)) {
            return;
        }

        double progress = session.progress(now);
        int percent = (int) Math.floor(progress * 100.0D);
        int segments = Math.max(5, plugin.getConfig().getInt("revive.display.actionbar.bar-segments", 10));
        int filled = Math.max(0, Math.min(segments, (int) Math.floor(progress * segments)));
        String bar = "&a" + "█".repeat(filled) + "&7" + "█".repeat(segments - filled);

        String reviverText = plugin.getConfig().getString(
                "revive.display.actionbar.reviver-text",
                "&a✚ REVIVING &f%target% &8| %bar% &f%percent%%%"
        );
        String targetText = plugin.getConfig().getString(
                "revive.display.actionbar.target-text",
                "&a✚ &f%reviver% &ais reviving you &8| %bar% &f%percent%%%"
        );

        if (reviverText != null) {
            reviverText = replaceProgress(reviverText, target, reviver, bar, percent);
            reviver.sendActionBar(LEGACY.deserialize(reviverText));
        }
        if (targetText != null) {
            targetText = replaceProgress(targetText, target, reviver, bar, percent);
            target.sendActionBar(LEGACY.deserialize(targetText));
        }
    }

    private String replaceProgress(String text, Player target, Player reviver, String bar, int percent) {
        return text
                .replace("%target%", target.getName())
                .replace("%reviver%", reviver.getName())
                .replace("%bar%", bar)
                .replace("%percent%", Integer.toString(percent));
    }

    private void sendConfiguredActionBar(Player player, String path, String fallback) {
        String text = plugin.getConfig().getString(path, fallback);
        if (text == null || text.isBlank()) {
            player.sendActionBar(Component.empty());
            return;
        }
        player.sendActionBar(LEGACY.deserialize(text));
    }

    private double maxDistance() {
        return Math.max(0.1D, plugin.getConfig().getDouble("revive.max-distance", 1.0D));
    }

    private int requiredAmount() {
        return Math.max(1, plugin.getConfig().getInt("revive.item.amount", 1));
    }

    private Material requiredMaterial() {
        String configured = plugin.getConfig().getString("revive.item.material", "GOLDEN_APPLE");
        Material material = configured == null ? null : Material.matchMaterial(configured);
        return material == null ? Material.GOLDEN_APPLE : material;
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }
}
