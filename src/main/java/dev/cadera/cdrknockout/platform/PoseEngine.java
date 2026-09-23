package dev.cadera.cdrknockout.platform;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.core.KnockoutSession;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

import java.util.Locale;

/**
 * Applies the visual body pose used while a player is KNOCKED.
 *
 * Paper 1.21.11 exposes Entity#setPose(Pose, boolean). The previous
 * implementation only toggled Player#setSwimming(true), which changes the
 * swimming state but does not reliably force the client-visible body pose on
 * land. v1.0.2 keeps the swimming state for compatibility and additionally
 * fixes the native SWIMMING pose so the body is explicitly rendered prone.
 */
public final class PoseEngine {

    private final CdrKnockoutPlugin plugin;
    private final ClientPlatformResolver platformResolver;

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
        // No passengers, ArmorStands, mounts, or fake carrier entities are used.
        // Keep the vanilla swimming state for Java/Geyser compatibility, then
        // explicitly fix the body pose so it cannot snap back to STANDING on land.
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
        if (player == null || session == null || !player.isOnline() || player.isDead()) {
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

    public String modeName(Player player) {
        return modeFor(player).name();
    }

    public String engineName() {
        return "PAPER_FIXED_POSE";
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
