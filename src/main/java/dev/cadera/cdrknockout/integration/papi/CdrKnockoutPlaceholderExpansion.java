package dev.cadera.cdrknockout.integration.papi;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.api.CdrKnockoutApi;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class CdrKnockoutPlaceholderExpansion extends PlaceholderExpansion {

    private final CdrKnockoutPlugin plugin;
    private final CdrKnockoutApi api;

    public CdrKnockoutPlaceholderExpansion(CdrKnockoutPlugin plugin, CdrKnockoutApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cdrknockout";
    }

    @Override
    public @NotNull String getAuthor() {
        return "CADERA";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);

        if (key.equals("version")) {
            return api.getVersion();
        }
        if (player == null) {
            return "";
        }

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
            default -> null;
        };
    }

    private String state(Player player) {
        if (api.isDeathInProgress(player)) {
            return "DYING";
        }
        if (api.isBeingExecuted(player)) {
            return "EXECUTING";
        }
        if (api.isBeingRevived(player)) {
            return "REVIVING";
        }
        if (api.isKnocked(player)) {
            return "KNOCKED";
        }
        return "NORMAL";
    }

    private String rawTime(Player player) {
        if (!api.isKnocked(player)) {
            return "0";
        }
        long seconds = api.getRemainingSeconds(player);
        return seconds == Long.MAX_VALUE ? "∞" : Long.toString(seconds);
    }

    private String formattedTime(Player player) {
        if (!api.isKnocked(player)) {
            return "00:00";
        }
        long seconds = api.getRemainingSeconds(player);
        if (seconds == Long.MAX_VALUE) {
            return "∞";
        }
        long minutes = seconds / 60L;
        long remaining = seconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, remaining);
    }
}
