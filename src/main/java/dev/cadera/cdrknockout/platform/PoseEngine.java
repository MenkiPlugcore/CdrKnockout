package dev.cadera.cdrknockout.platform;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.core.KnockoutSession;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class PoseEngine {

    private final CdrKnockoutPlugin plugin;
    private final ClientPlatformResolver platformResolver;

    public PoseEngine(CdrKnockoutPlugin plugin, ClientPlatformResolver platformResolver) {
        this.plugin = plugin;
        this.platformResolver = platformResolver;
    }

    public void apply(Player player, KnockoutSession session) {
        if (player == null || session == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("knockout.pose.enabled", true)) {
            restore(player, session);
            return;
        }

        PoseMode mode = modeFor(player);
        switch (mode) {
            case SWIMMING -> {
                if (player.isSneaking()) {
                    player.setSneaking(false);
                }
                if (!player.isSwimming()) {
                    player.setSwimming(true);
                }
            }
            case CROUCH -> {
                if (player.isSwimming()) {
                    player.setSwimming(false);
                }
                if (!player.isSneaking()) {
                    player.setSneaking(true);
                }
            }
            case NONE -> restore(player, session);
        }
    }

    public void restore(Player player, KnockoutSession session) {
        if (player == null || session == null || !player.isOnline() || player.isDead()) {
            return;
        }
        player.setSwimming(session.originalSwimming());
        player.setSneaking(session.originalSneaking());
    }

    public String modeName(Player player) {
        return modeFor(player).name();
    }

    public PoseMode modeFor(Player player) {
        ClientPlatform platform = platformResolver.resolve(player);
        String path = switch (platform) {
            case BEDROCK -> "compatibility.client.pose.bedrock-mode";
            case JAVA -> "compatibility.client.pose.java-mode";
            case UNKNOWN -> "compatibility.client.pose.unknown-mode";
        };

        String fallback = platform == ClientPlatform.BEDROCK ? "SWIMMING" : "SWIMMING";
        String configured = plugin.getConfig().getString(path, fallback);
        if (configured == null) {
            configured = fallback;
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
