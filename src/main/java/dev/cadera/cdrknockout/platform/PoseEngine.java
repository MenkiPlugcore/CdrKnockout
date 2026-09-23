package dev.cadera.cdrknockout.platform;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.core.KnockoutSession;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Applies the visual body pose used while a player is KNOCKED.
 *
 * Paper can force the server-side/player-observer pose with setPose(..., true),
 * but the local Java client can still render its own player using its locally
 * calculated standing pose. v1.0.3 therefore combines the fixed Paper pose
 * with a client-only invisible collision trigger at the player's head block.
 * The fake block exists only for the knocked Java client and is restored when
 * the KO state ends. No world block is modified.
 */
public final class PoseEngine {

    private final CdrKnockoutPlugin plugin;
    private final ClientPlatformResolver platformResolver;
    private final Map<UUID, Location> selfViewCollisionBlocks = new HashMap<>();

    public PoseEngine(CdrKnockoutPlugin plugin, ClientPlatformResolver platformResolver) {
        this.plugin = plugin;
        this.platformResolver = platformResolver;
    }

    public void apply(Player player, KnockoutSession session) {
        if (player == null || session == null || !player.isOnline() || player.isDead()) {
            return;
        }
        if (!plugin.getConfig().getBoolean("knockout.pose.enabled", true)) {
            restore(player, session);
            return;
        }

        PoseMode mode = modeFor(player);
        switch (mode) {
            case SWIMMING -> applyProne(player);
            case CROUCH -> applyCrouch(player);
            case NONE -> restore(player, session);
        }
    }

    private void applyProne(Player player) {
        // Local Java players are client-authoritative for parts of their own
        // pose rendering. A client-only barrier in the head block makes the
        // vanilla client conclude it cannot stand, which activates its native
        // crawl rendering. Other players continue to receive the fixed server
        // Pose.SWIMMING metadata below.
        if (shouldUseJavaSelfViewTrigger(player)) {
            ensureSelfViewCollision(player);
        } else {
            clearSelfViewCollision(player);
        }

        if (player.isSneaking()) {
            player.setSneaking(false);
        }
        if (!player.isSwimming()) {
            player.setSwimming(true);
        }
        if (player.getPose() != Pose.SWIMMING || !player.hasFixedPose()) {
            player.setPose(Pose.SWIMMING, true);
        }
    }

    private void applyCrouch(Player player) {
        clearSelfViewCollision(player);
        if (player.isSwimming()) {
            player.setSwimming(false);
        }
        if (!player.isSneaking()) {
            player.setSneaking(true);
        }
        if (player.getPose() != Pose.SNEAKING || !player.hasFixedPose()) {
            player.setPose(Pose.SNEAKING, true);
        }
    }

    public void restore(Player player, KnockoutSession session) {
        if (player == null || session == null) {
            return;
        }

        // Always forget/restore the client-only collision, including quit paths.
        clearSelfViewCollision(player);

        if (!player.isOnline() || player.isDead()) {
            return;
        }

        // Release the forced pose first, then rebuild the pre-KO swimming /
        // sneaking state that CdrKnockout already persists.
        player.setPose(Pose.STANDING, false);
        player.setSwimming(session.originalSwimming());
        player.setSneaking(session.originalSneaking());

        // Give the client an immediate matching pose instead of waiting for a
        // later entity tick. The pose is deliberately not fixed after restore.
        if (session.originalSwimming()) {
            player.setPose(Pose.SWIMMING, false);
        } else if (session.originalSneaking()) {
            player.setPose(Pose.SNEAKING, false);
        } else {
            player.setPose(Pose.STANDING, false);
        }
    }

    private boolean shouldUseJavaSelfViewTrigger(Player player) {
        if (!plugin.getConfig().getBoolean(
                "compatibility.client.pose.java-self-view-crawl-trigger",
                true
        )) {
            return false;
        }

        // UNKNOWN is treated as Java for this workaround. On a server without
        // Geyser/Floodgate hooks, a normal Java client resolves as UNKNOWN.
        return platformResolver.resolve(player) != ClientPlatform.BEDROCK;
    }

    private void ensureSelfViewCollision(Player player) {
        Location target = player.getLocation().getBlock().getLocation().add(0.0D, 1.0D, 0.0D);
        UUID uuid = player.getUniqueId();
        Location previous = selfViewCollisionBlocks.get(uuid);

        if (previous != null && !sameBlock(previous, target)) {
            restoreRealBlock(player, previous);
            selfViewCollisionBlocks.remove(uuid);
        }

        Block realBlock = target.getBlock();
        if (!realBlock.isPassable()) {
            // The real world already provides the collision needed by the
            // local client. Never hide/replace a real solid block with a fake.
            selfViewCollisionBlocks.remove(uuid);
            return;
        }

        // Barrier is invisible in normal gameplay and has a full collision
        // shape. sendBlockChange affects only this player's client.
        player.sendBlockChange(target, Material.BARRIER.createBlockData());
        selfViewCollisionBlocks.put(uuid, target.clone());
    }

    private void clearSelfViewCollision(Player player) {
        Location location = selfViewCollisionBlocks.remove(player.getUniqueId());
        if (location != null && player.isOnline()) {
            restoreRealBlock(player, location);
        }
    }

    private void restoreRealBlock(Player player, Location location) {
        if (location.getWorld() == null || !location.getWorld().equals(player.getWorld())) {
            return;
        }
        player.sendBlockChange(location, location.getBlock().getBlockData());
    }

    private boolean sameBlock(Location first, Location second) {
        if (first.getWorld() == null || second.getWorld() == null) {
            return false;
        }
        return first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    public String modeName(Player player) {
        return modeFor(player).name();
    }

    public String engineName() {
        return "PAPER_FIXED_POSE+JAVA_SELF_VIEW_COLLISION";
    }

    public PoseMode modeFor(Player player) {
        ClientPlatform platform = platformResolver.resolve(player);
        String path = switch (platform) {
            case BEDROCK -> "compatibility.client.pose.bedrock-mode";
            case JAVA -> "compatibility.client.pose.java-mode";
            case UNKNOWN -> "compatibility.client.pose.unknown-mode";
        };

        String configured = plugin.getConfig().getString(path, "SWIMMING");
        if (configured == null) {
            configured = "SWIMMING";
        }

        try {
            return PoseMode.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return PoseMode.SWIMMING;
        }
    }

    public enum PoseMode {
        SWIMMING,
        CROUCH,
        NONE
    }
}
