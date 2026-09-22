package dev.cadera.cdrknockout.execution;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.revive.ReviveManager;
import dev.cadera.cdrknockout.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ExecutionManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Set<Material> DEFAULT_EXECUTION_ITEMS = EnumSet.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD,
            Material.NETHERITE_SWORD
    );

    private final CdrKnockoutPlugin plugin;
    private final KnockoutManager knockoutManager;
    private final ReviveManager reviveManager;
    private final Messages messages;
    private final Map<UUID, ExecutionSession> sessionsByTarget = new HashMap<>();
    private final Map<UUID, UUID> targetByExecutor = new HashMap<>();
    private BukkitTask ticker;

    public ExecutionManager(
            CdrKnockoutPlugin plugin,
            KnockoutManager knockoutManager,
            ReviveManager reviveManager,
            Messages messages
    ) {
        this.plugin = plugin;
        this.knockoutManager = knockoutManager;
        this.reviveManager = reviveManager;
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
        debug("Execution configuration reloaded; active channels reset.");
    }

    public boolean isTargetBeingExecuted(Player player) {
        return player != null && sessionsByTarget.containsKey(player.getUniqueId());
    }

    public boolean isExecutor(Player player) {
        return player != null && targetByExecutor.containsKey(player.getUniqueId());
    }

    public boolean isExecutionIntent(Player executor, Player target) {
        return isEligible(executor, target, false);
    }

    public void handlePlayerUnavailable(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        UUID targetId = targetByExecutor.get(playerId);
        if (targetId != null) {
            cancelSession(targetId, true);
        }
        cancelSession(playerId, false);
    }

    public void onPlayerDamaged(Player player) {
        if (player == null) {
            return;
        }

        UUID targetId = targetByExecutor.get(player.getUniqueId());
        if (targetId != null && plugin.getConfig().getBoolean("execution.channel.cancel-on-damage", true)) {
            cancelSession(targetId, true);
        }

        if (plugin.getConfig().getBoolean("execution.channel.cancel-on-target-damage", false)
                && sessionsByTarget.containsKey(player.getUniqueId())) {
            cancelSession(player.getUniqueId(), true);
        }
    }

    public void onExecutorAttack(Player player) {
        if (player == null || !plugin.getConfig().getBoolean("execution.channel.cancel-on-attack", true)) {
            return;
        }
        UUID targetId = targetByExecutor.get(player.getUniqueId());
        if (targetId != null) {
            cancelSession(targetId, true);
        }
    }

    public void cancelTarget(Player target, boolean notify) {
        if (target != null) {
            cancelSession(target.getUniqueId(), notify);
        }
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("execution.enabled", true)) {
            if (!sessionsByTarget.isEmpty()) {
                cancelAll(false);
            }
            return;
        }

        long now = System.currentTimeMillis();

        for (UUID targetId : new ArrayList<>(sessionsByTarget.keySet())) {
            ExecutionSession session = sessionsByTarget.get(targetId);
            if (session == null) {
                continue;
            }

            Player target = plugin.getServer().getPlayer(session.targetId());
            Player executor = plugin.getServer().getPlayer(session.executorId());
            if (!validateSession(session, target, executor)) {
                cancelSession(targetId, true);
                continue;
            }

            if (now >= session.completesAtMillis()) {
                completeSession(session, target, executor);
                continue;
            }

            sendProgress(session, target, executor, now);
        }

        for (Player target : plugin.getServer().getOnlinePlayers()) {
            if (!knockoutManager.isKnocked(target)
                    || knockoutManager.isDeathInProgress(target)
                    || sessionsByTarget.containsKey(target.getUniqueId())
                    || reviveManager.isTargetBeingRevived(target)) {
                continue;
            }

            Player executor = findEligibleExecutor(target);
            if (executor != null) {
                startSession(target, executor, now);
            }
        }
    }

    private Player findEligibleExecutor(Player target) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player candidate : target.getWorld().getPlayers()) {
            if (!isEligible(candidate, target, true)) {
                continue;
            }
            double distance = candidate.getLocation().distanceSquared(target.getLocation());
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    private boolean isEligible(Player executor, Player target, boolean requireFreeTarget) {
        if (!plugin.getConfig().getBoolean("execution.enabled", true)
                || executor == null
                || target == null
                || executor.getUniqueId().equals(target.getUniqueId())
                || !executor.isOnline()
                || executor.isDead()
                || target.isDead()
                || knockoutManager.isKnocked(executor)
                || !knockoutManager.isKnocked(target)
                || knockoutManager.isDeathInProgress(target)
                || !executor.getWorld().equals(target.getWorld())
                || targetByExecutor.containsKey(executor.getUniqueId())) {
            return false;
        }

        if (requireFreeTarget && (sessionsByTarget.containsKey(target.getUniqueId())
                || reviveManager.isTargetBeingRevived(target))) {
            return false;
        }

        if (plugin.getConfig().getBoolean("execution.require-sneak", true) && !executor.isSneaking()) {
            return false;
        }

        String permission = plugin.getConfig().getString("execution.permission", "cdrknockout.execute");
        if (permission != null && !permission.isBlank() && !executor.hasPermission(permission)) {
            return false;
        }

        double maxDistance = maxDistance();
        if (executor.getLocation().distanceSquared(target.getLocation()) > maxDistance * maxDistance) {
            return false;
        }

        return matchesExecutionItem(executor.getInventory().getItemInMainHand());
    }

    private void startSession(Player target, Player executor, long now) {
        if (reviveManager.isTargetBeingRevived(target)) {
            return;
        }

        double durationSeconds = Math.max(0.5D, plugin.getConfig().getDouble("execution.duration-seconds", 3.0D));
        long completesAt = now + Math.max(1L, Math.round(durationSeconds * 1000.0D));
        Material lockedMaterial = executor.getInventory().getItemInMainHand().getType();

        ExecutionSession session = new ExecutionSession(
                target.getUniqueId(),
                executor.getUniqueId(),
                now,
                completesAt,
                executor.getLocation(),
                lockedMaterial
        );

        if (sessionsByTarget.putIfAbsent(target.getUniqueId(), session) != null) {
            return;
        }
        if (targetByExecutor.putIfAbsent(executor.getUniqueId(), target.getUniqueId()) != null) {
            sessionsByTarget.remove(target.getUniqueId(), session);
            return;
        }

        reviveManager.cancelTarget(target, false);
        executor.sendMessage(messages.format("execution-start-executor", "%target%", target.getName()));
        target.sendMessage(messages.format("execution-start-target", "%executor%", executor.getName()));
        sendProgress(session, target, executor, now);
        debug("Execution started: " + executor.getName() + " -> " + target.getName());
    }

    private boolean validateSession(ExecutionSession session, Player target, Player executor) {
        if (target == null || executor == null
                || !target.isOnline() || !executor.isOnline()
                || target.isDead() || executor.isDead()
                || !knockoutManager.isKnocked(target)
                || knockoutManager.isDeathInProgress(target)
                || knockoutManager.isKnocked(executor)
                || !target.getWorld().equals(executor.getWorld())) {
            return false;
        }

        if (plugin.getConfig().getBoolean("execution.require-sneak", true) && !executor.isSneaking()) {
            return false;
        }

        String permission = plugin.getConfig().getString("execution.permission", "cdrknockout.execute");
        if (permission != null && !permission.isBlank() && !executor.hasPermission(permission)) {
            return false;
        }

        double maxDistance = maxDistance();
        if (target.getLocation().distanceSquared(executor.getLocation()) > maxDistance * maxDistance) {
            return false;
        }

        ItemStack mainHand = executor.getInventory().getItemInMainHand();
        if (!matchesExecutionItem(mainHand)) {
            return false;
        }
        if (plugin.getConfig().getBoolean("execution.channel.cancel-on-item-change", true)
                && mainHand.getType() != session.lockedMaterial()) {
            return false;
        }

        if (plugin.getConfig().getBoolean("execution.channel.cancel-on-move", false)) {
            Location start = session.executorStartLocation();
            Location current = executor.getLocation();
            if (start.getWorld() == null || !start.getWorld().equals(current.getWorld())) {
                return false;
            }
            double tolerance = Math.max(0.0D, plugin.getConfig().getDouble(
                    "execution.channel.move-tolerance",
                    0.10D
            ));
            if (start.distanceSquared(current) > tolerance * tolerance) {
                return false;
            }
        }

        return !reviveManager.isTargetBeingRevived(target);
    }

    private void completeSession(ExecutionSession session, Player target, Player executor) {
        removeSession(session);

        if (!knockoutManager.isKnocked(target) || knockoutManager.isDeathInProgress(target)) {
            executor.sendMessage(messages.format("execution-failed-internal"));
            return;
        }

        executor.sendMessage(messages.format("execution-success-executor", "%target%", target.getName()));
        target.sendMessage(messages.format("execution-success-target", "%executor%", executor.getName()));
        sendConfiguredActionBar(executor, "execution.display.actionbar.success-executor", "&4&lEXECUTION COMPLETE");
        sendConfiguredActionBar(target, "execution.display.actionbar.success-target", "&4&lEXECUTED");

        if (!knockoutManager.forceDeath(target)) {
            executor.sendMessage(messages.format("execution-failed-internal"));
            debug("Execution forceDeath failed: " + executor.getName() + " -> " + target.getName());
            return;
        }

        debug("Execution completed: " + executor.getName() + " -> " + target.getName());
    }

    private void cancelSession(UUID targetId, boolean notify) {
        ExecutionSession session = sessionsByTarget.remove(targetId);
        if (session == null) {
            return;
        }
        targetByExecutor.remove(session.executorId(), session.targetId());

        Player target = plugin.getServer().getPlayer(session.targetId());
        Player executor = plugin.getServer().getPlayer(session.executorId());

        if (notify) {
            if (executor != null && executor.isOnline()) {
                executor.sendMessage(messages.format("execution-cancelled-executor"));
                sendConfiguredActionBar(executor, "execution.display.actionbar.cancelled", "&c&lEXECUTION INTERRUPTED");
            }
            if (target != null && target.isOnline() && knockoutManager.isKnocked(target)) {
                target.sendMessage(messages.format("execution-cancelled-target"));
            }
        }

        debug("Execution cancelled: target=" + session.targetId() + " executor=" + session.executorId());
    }

    private void cancelAll(boolean notify) {
        for (UUID targetId : new ArrayList<>(sessionsByTarget.keySet())) {
            cancelSession(targetId, notify);
        }
        sessionsByTarget.clear();
        targetByExecutor.clear();
    }

    private void removeSession(ExecutionSession session) {
        sessionsByTarget.remove(session.targetId(), session);
        targetByExecutor.remove(session.executorId(), session.targetId());
    }

    private void sendProgress(ExecutionSession session, Player target, Player executor, long now) {
        if (!plugin.getConfig().getBoolean("execution.display.actionbar.enabled", true)) {
            return;
        }

        double progress = session.progress(now);
        int percent = (int) Math.floor(progress * 100.0D);
        int segments = Math.max(5, plugin.getConfig().getInt("execution.display.actionbar.bar-segments", 10));
        int filled = Math.max(0, Math.min(segments, (int) Math.floor(progress * segments)));
        String bar = "&4" + "█".repeat(filled) + "&7" + "█".repeat(segments - filled);

        String executorText = plugin.getConfig().getString(
                "execution.display.actionbar.executor-text",
                "&4☠ EXECUTING &f%target% &8| %bar% &f%percent%%%"
        );
        String targetText = plugin.getConfig().getString(
                "execution.display.actionbar.target-text",
                "&4☠ &f%executor% &cis executing you &8| %bar% &f%percent%%%"
        );

        if (executorText != null) {
            executor.sendActionBar(LEGACY.deserialize(replaceProgress(executorText, target, executor, bar, percent)));
        }
        if (targetText != null) {
            target.sendActionBar(LEGACY.deserialize(replaceProgress(targetText, target, executor, bar, percent)));
        }
    }

    private String replaceProgress(String text, Player target, Player executor, String bar, int percent) {
        return text
                .replace("%target%", target.getName())
                .replace("%executor%", executor.getName())
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

    private boolean matchesExecutionItem(ItemStack stack) {
        if (!plugin.getConfig().getBoolean("execution.item.required", true)) {
            return true;
        }
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return configuredExecutionItems().contains(stack.getType());
    }

    private Set<Material> configuredExecutionItems() {
        List<String> configured = plugin.getConfig().getStringList("execution.item.allowed-materials");
        if (configured.isEmpty()) {
            return DEFAULT_EXECUTION_ITEMS;
        }

        Set<Material> materials = new HashSet<>();
        for (String entry : configured) {
            Material material = Material.matchMaterial(entry);
            if (material != null) {
                materials.add(material);
            }
        }
        return materials.isEmpty() ? DEFAULT_EXECUTION_ITEMS : materials;
    }

    private double maxDistance() {
        return Math.max(0.1D, plugin.getConfig().getDouble("execution.max-distance", 1.0D));
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] [Execution] " + message);
        }
    }
}
