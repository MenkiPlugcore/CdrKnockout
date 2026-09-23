package dev.cadera.cdrknockout.integration.papi;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.api.CdrKnockoutApi;
import dev.cadera.cdrknockout.gameplay.DistressManager;
import dev.cadera.cdrknockout.gameplay.SelfReviveManager;
import dev.cadera.cdrknockout.gameplay.StatisticsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class CdrKnockoutPlaceholderExpansion extends PlaceholderExpansion {

    private final CdrKnockoutPlugin plugin;
    private final CdrKnockoutApi api;
    private final StatisticsManager statistics;
    private final SelfReviveManager selfReviveManager;
    private final DistressManager distressManager;

    public CdrKnockoutPlaceholderExpansion(
            CdrKnockoutPlugin plugin,
            CdrKnockoutApi api,
            StatisticsManager statistics,
            SelfReviveManager selfReviveManager,
            DistressManager distressManager
    ) {
        this.plugin = plugin;
        this.api = api;
        this.statistics = statistics;
        this.selfReviveManager = selfReviveManager;
        this.distressManager = distressManager;
    }

    @Override public @NotNull String getIdentifier() { return "cdrknockout"; }
    @Override public @NotNull String getAuthor() { return "CADERA"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("version")) {
            return api.getVersion();
        }
        if (player == null) {
            return "";
        }

        StatisticsManager.StatsSnapshot stats = statistics.snapshot(player);
        return switch (key) {
            case "state" -> state(player);
            case "is_knocked" -> Boolean.toString(api.isKnocked(player));
            case "death_in_progress" -> Boolean.toString(api.isDeathInProgress(player));
            case "time" -> rawTime(player);
            case "time_formatted" -> formattedTime(player);
            case "platform" -> api.getClientPlatform(player);
            case "pose" -> api.getPoseMode(player);
            case "being_revived" -> Boolean.toString(api.isBeingRevived(player));
            case "reviver" -> Boolean.toString(api.isReviver(player));
            case "being_executed" -> Boolean.toString(api.isBeingExecuted(player));
            case "executor" -> Boolean.toString(api.isExecutor(player));
            case "self_reviving" -> Boolean.toString(selfReviveManager.isSelfReviving(player));
            case "self_revive_cooldown" -> Long.toString(selfReviveManager.cooldownRemainingSeconds(player));
            case "distress_cooldown" -> Long.toString(distressManager.cooldownRemainingSeconds(player));
            case "stat_knockouts" -> Long.toString(stats.knockouts());
            case "stat_revives_received" -> Long.toString(stats.revivesReceived());
            case "stat_knockout_deaths" -> Long.toString(stats.knockoutDeaths());
            case "stat_self_revives" -> Long.toString(stats.selfRevives());
            case "stat_distress" -> Long.toString(stats.distressSignals());
            case "stat_medkit_uses" -> Long.toString(stats.medicalKitUses());
            default -> null;
        };
    }

    private String state(Player player) {
        if (api.isDeathInProgress(player)) return "DYING";
        if (api.isBeingExecuted(player)) return "EXECUTING";
        if (api.isBeingRevived(player)) return "REVIVING";
        if (selfReviveManager.isSelfReviving(player)) return "SELF_REVIVING";
        if (api.isKnocked(player)) return "KNOCKED";
        return "NORMAL";
    }

    private String rawTime(Player player) {
        if (!api.isKnocked(player)) return "0";
        long seconds = api.getRemainingSeconds(player);
        return seconds == Long.MAX_VALUE ? "∞" : Long.toString(seconds);
    }

    private String formattedTime(Player player) {
        if (!api.isKnocked(player)) return "00:00";
        long seconds = api.getRemainingSeconds(player);
        if (seconds == Long.MAX_VALUE) return "∞";
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }
}
