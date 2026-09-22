package dev.cadera.cdrknockout.revive;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.execution.ExecutionManager;
import dev.cadera.cdrknockout.revive.requirement.RequirementEngine;
import dev.cadera.cdrknockout.revive.requirement.RequirementResult;
import dev.cadera.cdrknockout.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
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
    private final RequirementEngine requirementEngine;
    private final Map<UUID, ReviveSession> sessionsByTarget = new HashMap<>();
    private final Map<UUID, UUID> targetByReviver = new HashMap<>();
    private final Map<UUID, Long> lastRequirementNotice = new HashMap<>();
    private ExecutionManager executionManager;
    private BukkitTask ticker;

    public ReviveManager(CdrKnockoutPlugin plugin, KnockoutManager knockoutManager, Messages messages) {
        this.plugin = plugin;
        this.knockoutManager = knockoutManager;
        this.messages = messages;
        this.requirementEngine = new RequirementEngine(plugin, messages);
    }

    public void setExecutionManager(ExecutionManager executionManager) {
        this.executionManager = executionManager;
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
        lastRequirementNotice.clear();
    }

    public void onReload() {
        cancelAll(true);
        lastRequirementNotice.clear();
        requirementEngine.reload();
        debug("Revive configuration reloaded; active channels reset.");
    }

    public boolean isTargetBeingRevived(Player player) {
        return player != null && sessionsByTarget.containsKey(player.getUniqueId());
    }

    public boolean isReviver(Player player) {
        return player != null && targetByReviver.containsKey(player.getUniqueId());
    }

    public void cancelTarget(Player target, boolean notify) {
        if (target != null) {
            cancelSession(target.getUniqueId(), notify);
        }
    }

    public void handlePlayerUnavailable(Player player) {
        UUID targetId = targetByReviver.get(player.getUniqueId());
        if (targetId != null) {
            cancelSession(targetId, true);
        }
        cancelSession(player.getUniqueId(), false);
        lastRequirementNotice.remove(player.getUniqueId());
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
                || !requirementEngine.isItemRequirementEnabled()
                || !player.isSneaking()
                || !requirementEngine.matchesRequiredItem(player.getInventory().getItemInMainHand())) {
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
            if (executionManager != null && executionManager.isTargetBeingExecuted(target)) {
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
        return requirementEngine.matchesRequiredItem(stack);
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
            RequirementResult validation = validateSession(session, target, reviver);
            if (!validation.passed()) {
                if (reviver != null && reviver.isOnline() && !validation.failureMessage().isBlank()) {
                    reviver.sendMessage(validation.failureMessage());
                }
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
            if (!knockoutManager.isKnocked(target)
                    || sessionsByTarget.containsKey(target.getUniqueId())
                    || (executionManager != null && executionManager.isTargetBeingExecuted(target))) {
                continue;
            }
            EligibleReviver eligible = findEligibleReviver(target, now);
            if (eligible != null) {
                startSession(target, eligible.player(), eligible.requirements(), now);
            }
        }
    }

    private EligibleReviver findEligibleReviver(Player target, long now) {
        Player nearest = null;
        RequirementResult nearestRequirements = null;
        double nearestDistance = Double.MAX_VALUE;
        double maxDistance = maxDistance();
        double maxDistanceSquared = maxDistance * maxDistance;

        for (Player candidate : target.getWorld().getPlayers()) {
            if (candidate.getUniqueId().equals(target.getUniqueId())
                    || targetByReviver.containsKey(candidate.getUniqueId())
                    || knockoutManager.isKnocked(candidate)
                    || candidate.isDead()
                    || !candidate.isOnline()
                    || !candidate.isSneaking()) {
                continue;
            }

            if (executionManager != null
                    && (executionManager.isExecutor(candidate)
                    || executionManager.isExecutionIntent(candidate, target))) {
                continue;
            }

            double distance = candidate.getLocation().distanceSquared(target.getLocation());
            if (distance > maxDistanceSquared) {
                continue;
            }

            RequirementResult requirements = requirementEngine.evaluate(candidate);
            if (!requirements.passed()) {
                notifyRequirementFailure(candidate, requirements, now);
                continue;
            }

            if (distance < nearestDistance) {
                nearest = candidate;
                nearestRequirements = requirements;
                nearestDistance = distance;
            }
        }

        return nearest == null ? null : new EligibleReviver(nearest, nearestRequirements);
    }

    private void startSession(Player target, Player reviver, RequirementResult requirements, long now) {
        if (executionManager != null
                && (executionManager.isTargetBeingExecuted(target)
                || executionManager.isExecutionIntent(reviver, target))) {
            return;
        }

        double durationSeconds = Math.max(0.5D, plugin.getConfig().getDouble("revive.duration-seconds", 8.0D));
        long completesAt = now + Math.max(1L, Math.round(durationSeconds * 1000.0D));

        ReviveSession session = new ReviveSession(
                target.getUniqueId(),
                reviver.getUniqueId(),
                now,
                completesAt,
                reviver.getLocation(),
                requirements.selected()
        );
        sessionsByTarget.put(target.getUniqueId(), session);
        targetByReviver.put(reviver.getUniqueId(), target.getUniqueId());
        lastRequirementNotice.remove(reviver.getUniqueId());

        reviver.sendMessage(messages.format("revive-start-reviver", "%target%", target.getName()));
        target.sendMessage(messages.format("revive-start-target", "%reviver%", reviver.getName()));
        sendProgress(session, target, reviver, now);
        debug("Revive started: " + reviver.getName() + " -> " + target.getName()
                + " requirements=" + session.selectedRequirements());
    }

    private RequirementResult validateSession(ReviveSession session, Player target, Player reviver) {
        if (target == null || reviver == null
                || !target.isOnline() || !reviver.isOnline()
                || target.isDead() || reviver.isDead()
                || !knockoutManager.isKnocked(target)
                || knockoutManager.isKnocked(reviver)
                || !target.getWorld().equals(reviver.getWorld())
                || !reviver.isSneaking()
                || (executionManager != null && (executionManager.isTargetBeingExecuted(target)
                || executionManager.isExecutor(reviver)))) {
            return RequirementResult.failure("");
        }

        double maxDistance = maxDistance();
        if (target.getLocation().distanceSquared(reviver.getLocation()) > maxDistance * maxDistance) {
            return RequirementResult.failure("");
        }

        if (plugin.getConfig().getBoolean("revive.channel.cancel-on-move", false)) {
            Location start = session.reviverStartLocation();
            Location current = reviver.getLocation();
            if (!start.getWorld().equals(current.getWorld())) {
                return RequirementResult.failure("");
            }
            double tolerance = Math.max(0.0D, plugin.getConfig().getDouble("revive.channel.move-tolerance", 0.10D));
            if (start.distanceSquared(current) > tolerance * tolerance) {
                return RequirementResult.failure("");
            }
        }

        return requirementEngine.validateSelected(reviver, session.selectedRequirements());
    }

    private void completeSession(ReviveSession session, Player target, Player reviver) {
        if (executionManager != null && executionManager.isTargetBeingExecuted(target)) {
            cancelSession(session.targetId(), true);
            return;
        }

        RequirementResult validation = requirementEngine.validateSelected(reviver, session.selectedRequirements());
        if (!validation.passed()) {
            if (!validation.failureMessage().isBlank()) {
                reviver.sendMessage(validation.failureMessage());
            }
            cancelSession(session.targetId(), true);
            return;
        }

        if (!requirementEngine.commit(reviver, session.selectedRequirements())) {
            reviver.sendMessage(messages.format("revive-requirement-payment-failed"));
            cancelSession(session.targetId(), true);
            return;
        }

        removeSession(session);

        double health = Math.max(0.5D, plugin.getConfig().getDouble("revive.result.health", 6.0D));
        int resistanceSeconds = Math.max(0, plugin.getConfig().getInt("revive.result.resistance-seconds", 3));
        if (!knockoutManager.revive(target, health, resistanceSeconds)) {
            reviver.sendMessage(messages.format("revive-failed-internal"));
            return;
        }

        reviver.sendMessage(messages.format("revive-success-reviver", "%target%", target.getName()));
        sendConfiguredActionBar(reviver, "revive.display.actionbar.success-reviver", "&a&lREVIVE SUCCESS");
        sendConfiguredActionBar(target, "revive.display.actionbar.success-target", "&a&lREVIVED");
        debug("Revive completed: " + reviver.getName() + " -> " + target.getName()
                + " requirements=" + session.selectedRequirements());
    }

    private void notifyRequirementFailure(Player player, RequirementResult result, long now) {
        if (result.failureMessage().isBlank()) {
            return;
        }
        long cooldown = Math.max(500L, plugin.getConfig().getLong(
                "revive.requirements.failure-message-cooldown-ms",
                2000L
        ));
        long last = lastRequirementNotice.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < cooldown) {
            return;
        }
        lastRequirementNotice.put(player.getUniqueId(), now);
        player.sendMessage(result.failureMessage());
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

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    private record EligibleReviver(Player player, RequirementResult requirements) {
    }
}
