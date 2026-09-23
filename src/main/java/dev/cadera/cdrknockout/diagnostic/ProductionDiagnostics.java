package dev.cadera.cdrknockout.diagnostic;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.core.KnockoutPersistence;
import dev.cadera.cdrknockout.gameplay.StatisticsManager;
import dev.cadera.cdrknockout.integration.AxGravesCompatibility;
import dev.cadera.cdrknockout.integration.PlaceholderApiIntegration;
import dev.cadera.cdrknockout.platform.ClientPlatformResolver;
import dev.cadera.cdrknockout.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/** Production-oriented runtime validation and operator diagnostics. */
public final class ProductionDiagnostics {

    private final CdrKnockoutPlugin plugin;
    private final KnockoutManager knockoutManager;
    private final KnockoutPersistence persistence;
    private final StatisticsManager statistics;
    private final AxGravesCompatibility axGraves;
    private final ClientPlatformResolver platformResolver;
    private final PlaceholderApiIntegration placeholderApi;
    private final Messages messages;

    public ProductionDiagnostics(
            CdrKnockoutPlugin plugin,
            KnockoutManager knockoutManager,
            KnockoutPersistence persistence,
            StatisticsManager statistics,
            AxGravesCompatibility axGraves,
            ClientPlatformResolver platformResolver,
            PlaceholderApiIntegration placeholderApi,
            Messages messages
    ) {
        this.plugin = plugin;
        this.knockoutManager = knockoutManager;
        this.persistence = persistence;
        this.statistics = statistics;
        this.axGraves = axGraves;
        this.platformResolver = platformResolver;
        this.placeholderApi = placeholderApi;
        this.messages = messages;
    }

    public Report inspect() {
        List<Check> checks = new ArrayList<>();

        int javaFeature = Runtime.version().feature();
        if (javaFeature >= 21) {
            checks.add(ok("Java", "Java " + javaFeature));
        } else {
            checks.add(error("Java", "Java 21+ required, detected " + javaFeature));
        }

        String expectedMinecraft = plugin.getConfig().getString(
                "production.expected-minecraft-version",
                "1.21.11"
        );
        String detectedMinecraft = Bukkit.getMinecraftVersion();
        if (expectedMinecraft == null || expectedMinecraft.isBlank() || expectedMinecraft.equals(detectedMinecraft)) {
            checks.add(ok("Minecraft", detectedMinecraft));
        } else if (plugin.getConfig().getBoolean("production.warn-unsupported-minecraft-version", true)) {
            checks.add(warn("Minecraft", "expected " + expectedMinecraft + ", detected " + detectedMinecraft));
        } else {
            checks.add(ok("Minecraft", detectedMinecraft + " (version warning disabled)"));
        }

        checks.add(checkDataFolder());
        validateConfiguration(checks);

        checks.add(ok(
                "Knockout runtime",
                knockoutManager.activeSessionCount() + " active, "
                        + knockoutManager.deathInProgressCount() + " managed death(s)"
        ));
        checks.add(ok(
                "Persistence",
                persistence.enabled()
                        ? "enabled, " + persistence.storedCount() + " stored session(s)"
                        : "disabled by config"
        ));
        checks.add(ok(
                "Statistics",
                statistics != null && statistics.enabled() ? "enabled" : "disabled by config"
        ));

        if (axGraves != null) {
            String state = axGraves.statusName();
            if ("FAILED".equalsIgnoreCase(state)) {
                checks.add(warn("AxGraves", state + detailSuffix(axGraves.hookFailure())));
            } else {
                checks.add(ok("AxGraves", state + " (" + axGraves.detectedVersion() + ")"));
            }
        }

        if (platformResolver != null) {
            String failure = joinFailures(platformResolver.geyserFailure(), platformResolver.floodgateFailure());
            if (!failure.isBlank()) {
                checks.add(warn("Crossplay detector", platformResolver.statusName() + ": " + failure));
            } else {
                checks.add(ok("Crossplay detector", platformResolver.statusName()));
            }
        }

        if (placeholderApi != null) {
            if ("FAILED".equalsIgnoreCase(placeholderApi.statusName())) {
                checks.add(warn("PlaceholderAPI", "FAILED" + detailSuffix(placeholderApi.failure())));
            } else {
                checks.add(ok("PlaceholderAPI", placeholderApi.statusName()));
            }
        }

        if (plugin.getConfig().getBoolean("revive.requirements.money.enabled", false)) {
            Plugin vault = plugin.getServer().getPluginManager().getPlugin("Vault");
            if (vault == null || !vault.isEnabled()) {
                checks.add(warn("Vault requirement", "money requirement enabled but Vault is unavailable"));
            }
        }
        if (plugin.getConfig().getBoolean("revive.requirements.auraskills.enabled", false)) {
            Plugin auraSkills = plugin.getServer().getPluginManager().getPlugin("AuraSkills");
            if (auraSkills == null || !auraSkills.isEnabled()) {
                checks.add(warn("AuraSkills requirement", "AuraSkills requirement enabled but plugin is unavailable"));
            }
        }

        Severity overall = Severity.OK;
        for (Check check : checks) {
            if (check.severity() == Severity.ERROR) {
                overall = Severity.ERROR;
                break;
            }
            if (check.severity() == Severity.WARN) {
                overall = Severity.WARN;
            }
        }
        return new Report(overall, List.copyOf(checks));
    }

    public void send(CommandSender sender) {
        Report report = inspect();
        sender.sendMessage(messages.color("&8&m----------------------------------------"));
        sender.sendMessage(messages.color("&c&lCdrKnockout &7- &fProduction Doctor"));
        sender.sendMessage(messages.color("&7Version: &f" + plugin.getDescription().getVersion()));
        sender.sendMessage(messages.color("&7Overall: " + color(report.overall()) + report.overall().name()));
        for (Check check : report.checks()) {
            sender.sendMessage(messages.color(
                    symbol(check.severity()) + " &7" + check.name() + ": "
                            + color(check.severity()) + check.detail()
            ));
        }
        sender.sendMessage(messages.color("&8&m----------------------------------------"));
    }

    public void logStartupReport() {
        if (!plugin.getConfig().getBoolean("production.startup-diagnostics", true)) {
            return;
        }
        logReport("startup");
    }

    public void logReloadReport() {
        if (!plugin.getConfig().getBoolean("production.log-on-reload", true)) {
            return;
        }
        logReport("reload");
    }

    private void logReport(String phase) {
        Report report = inspect();
        plugin.getLogger().info("Production diagnostics (" + phase + "): " + report.overall()
                + " | activeKO=" + knockoutManager.activeSessionCount()
                + " persisted=" + persistence.storedCount());
        for (Check check : report.checks()) {
            if (check.severity() == Severity.OK) {
                continue;
            }
            Level level = check.severity() == Severity.ERROR ? Level.SEVERE : Level.WARNING;
            plugin.getLogger().log(level, "[Doctor] " + check.name() + ": " + check.detail());
        }
    }

    private Check checkDataFolder() {
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            return error("Data folder", "cannot create " + folder.getAbsolutePath());
        }
        Path probe = null;
        try {
            probe = Files.createTempFile(folder.toPath(), ".cdrko-write-test-", ".tmp");
            return ok("Data folder", "writable");
        } catch (IOException exception) {
            return error("Data folder", "not writable: " + exception.getMessage());
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (IOException ignored) {
                    // Best-effort probe cleanup.
                }
            }
        }
    }

    private void validateConfiguration(List<Check> checks) {
        checkEnum(checks, "World policy", "knockout.worlds.mode", "ALL", Set.of("ALL", "WHITELIST", "BLACKLIST"));
        checkEnum(checks, "Teleport policy", "stability.teleport.mode", "BLOCK", Set.of("BLOCK", "FOLLOW"));
        checkEnum(checks, "Java pose", "compatibility.client.pose.java-mode", "SWIMMING", Set.of("SWIMMING", "CROUCH", "NONE"));
        checkEnum(checks, "Bedrock pose", "compatibility.client.pose.bedrock-mode", "SWIMMING", Set.of("SWIMMING", "CROUCH", "NONE"));
        checkEnum(checks, "Unknown-client pose", "compatibility.client.pose.unknown-mode", "SWIMMING", Set.of("SWIMMING", "CROUCH", "NONE"));
        checkEnum(checks, "Revive requirement mode", "revive.requirements.mode", "ALL", Set.of("ALL", "ANY"));
        checkEnum(checks, "Downed damage mode", "knockout.bleedout.downed-damage.default-mode", "REDUCE_TIMER",
                Set.of("IGNORE", "REDUCE_TIMER", "INSTANT_DEATH"));

        if (plugin.getConfig().getLong("knockout.duration-seconds", 60L) < 1L) {
            checks.add(warn("Knockout duration", "must be >= 1 second; runtime clamp will be used"));
        }
        if (plugin.getConfig().getDouble("revive.max-distance", 1.0D) <= 0.0D) {
            checks.add(warn("Revive distance", "must be > 0; runtime clamp will be used"));
        }
        if (plugin.getConfig().getDouble("revive.duration-seconds", 8.0D) <= 0.0D) {
            checks.add(warn("Revive duration", "must be > 0; runtime clamp will be used"));
        }
        if (plugin.getConfig().getBoolean("execution.enabled", true)
                && plugin.getConfig().getDouble("execution.duration-seconds", 3.0D) <= 0.0D) {
            checks.add(warn("Execution duration", "must be > 0; runtime clamp will be used"));
        }
        if (plugin.getConfig().getBoolean("gameplay.self-revive.enabled", true)
                && plugin.getConfig().getDouble("gameplay.self-revive.duration-seconds", 10.0D) <= 0.0D) {
            checks.add(warn("Self-revive duration", "must be > 0; runtime clamp will be used"));
        }
    }

    private void checkEnum(
            List<Check> checks,
            String name,
            String path,
            String fallback,
            Set<String> allowed
    ) {
        String value = plugin.getConfig().getString(path, fallback);
        value = value == null ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (allowed.contains(value)) {
            checks.add(ok(name, value));
        } else {
            checks.add(warn(name, "invalid value '" + value + "'; allowed=" + allowed));
        }
    }

    private String joinFailures(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        if (a.isBlank()) return b;
        if (b.isBlank()) return a;
        return a + " | " + b;
    }

    private String detailSuffix(String detail) {
        return detail == null || detail.isBlank() ? "" : ": " + detail;
    }

    private String symbol(Severity severity) {
        return switch (severity) {
            case OK -> "&a✔";
            case WARN -> "&e!";
            case ERROR -> "&c✖";
        };
    }

    private String color(Severity severity) {
        return switch (severity) {
            case OK -> "&a";
            case WARN -> "&e";
            case ERROR -> "&c";
        };
    }

    private Check ok(String name, String detail) {
        return new Check(Severity.OK, name, detail);
    }

    private Check warn(String name, String detail) {
        return new Check(Severity.WARN, name, detail);
    }

    private Check error(String name, String detail) {
        return new Check(Severity.ERROR, name, detail);
    }

    public enum Severity {
        OK,
        WARN,
        ERROR
    }

    public record Check(Severity severity, String name, String detail) {
    }

    public record Report(Severity overall, List<Check> checks) {
    }
}
