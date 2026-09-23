package dev.cadera.cdrknockout.command;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.diagnostic.ProductionDiagnostics;
import dev.cadera.cdrknockout.integration.AxGravesCompatibility;
import dev.cadera.cdrknockout.platform.ClientPlatform;
import dev.cadera.cdrknockout.platform.ClientPlatformResolver;
import dev.cadera.cdrknockout.platform.PoseEngine;
import dev.cadera.cdrknockout.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CdrKnockoutCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "reload", "knockout", "revive", "kill", "status", "compat", "platform", "doctor"
    );

    private final CdrKnockoutPlugin plugin;
    private final KnockoutManager manager;
    private final Messages messages;
    private final AxGravesCompatibility axGravesCompatibility;
    private final ClientPlatformResolver platformResolver;
    private final PoseEngine poseEngine;
    private final ProductionDiagnostics diagnostics;

    public CdrKnockoutCommand(CdrKnockoutPlugin plugin, KnockoutManager manager, Messages messages) {
        this(plugin, manager, messages, null, null, null, null);
    }

    public CdrKnockoutCommand(
            CdrKnockoutPlugin plugin,
            KnockoutManager manager,
            Messages messages,
            AxGravesCompatibility axGravesCompatibility
    ) {
        this(plugin, manager, messages, axGravesCompatibility, null, null, null);
    }

    public CdrKnockoutCommand(
            CdrKnockoutPlugin plugin,
            KnockoutManager manager,
            Messages messages,
            AxGravesCompatibility axGravesCompatibility,
            ClientPlatformResolver platformResolver,
            PoseEngine poseEngine
    ) {
        this(plugin, manager, messages, axGravesCompatibility, platformResolver, poseEngine, null);
    }

    public CdrKnockoutCommand(
            CdrKnockoutPlugin plugin,
            KnockoutManager manager,
            Messages messages,
            AxGravesCompatibility axGravesCompatibility,
            ClientPlatformResolver platformResolver,
            PoseEngine poseEngine,
            ProductionDiagnostics diagnostics
    ) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
        this.axGravesCompatibility = axGravesCompatibility;
        this.platformResolver = platformResolver;
        this.poseEngine = poseEngine;
        this.diagnostics = diagnostics;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("cdrknockout.command")) {
            sender.sendMessage(messages.format("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(messages.format("usage"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!checkPermission(sender, "cdrknockout.command.reload")) {
                return true;
            }
            plugin.reloadRuntimeConfig();
            sender.sendMessage(messages.format("reload"));
            return true;
        }

        if (sub.equals("compat")) {
            if (!checkPermission(sender, "cdrknockout.command.compat")) {
                return true;
            }
            handleCompatibility(sender);
            return true;
        }

        if (sub.equals("doctor")) {
            if (!checkPermission(sender, "cdrknockout.command.doctor")) {
                return true;
            }
            handleDoctor(sender);
            return true;
        }

        if (!SUBCOMMANDS.contains(sub) || args.length < 2) {
            sender.sendMessage(messages.format("usage"));
            return true;
        }

        if (!checkPermission(sender, "cdrknockout.command." + sub)) {
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messages.format("player-not-found", "%player%", args[1]));
            return true;
        }

        switch (sub) {
            case "knockout" -> handleKnockout(sender, target);
            case "revive" -> handleRevive(sender, target);
            case "kill" -> handleKill(sender, target);
            case "status" -> handleStatus(sender, target);
            case "platform" -> handlePlatform(sender, target);
            default -> sender.sendMessage(messages.format("usage"));
        }
        return true;
    }

    private void handleKnockout(CommandSender sender, Player target) {
        if (!manager.knockout(target, EntityDamageEvent.DamageCause.CUSTOM, true)) {
            sender.sendMessage(messages.format("already-knocked", "%player%", target.getName()));
            return;
        }
        sender.sendMessage(messages.format("knocked-admin", "%player%", target.getName()));
    }

    private void handleRevive(CommandSender sender, Player target) {
        if (!manager.revive(target)) {
            sender.sendMessage(messages.format("not-knocked", "%player%", target.getName()));
            return;
        }
        sender.sendMessage(messages.format("revived-admin", "%player%", target.getName()));
    }

    private void handleKill(CommandSender sender, Player target) {
        if (!manager.forceDeath(target)) {
            sender.sendMessage(messages.format("not-knocked", "%player%", target.getName()));
            return;
        }
        sender.sendMessage(messages.format("killed-admin", "%player%", target.getName()));
    }

    private void handleStatus(CommandSender sender, Player target) {
        if (!manager.isKnocked(target)) {
            sender.sendMessage(messages.format("status-normal", "%player%", target.getName()));
            return;
        }
        long remaining = manager.getRemainingSeconds(target);
        String time = remaining == Long.MAX_VALUE ? "∞" : Long.toString(remaining);
        sender.sendMessage(messages.format("status-knocked", "%player%", target.getName(), "%time%", time));
    }

    private void handlePlatform(CommandSender sender, Player target) {
        sender.sendMessage(messages.color("&8&m----------------------------------------"));
        sender.sendMessage(messages.color("&c&lCdrKnockout &7- &fClient Platform"));
        sender.sendMessage(messages.color("&7Player: &f" + target.getName()));

        if (platformResolver == null) {
            sender.sendMessage(messages.color("&7Platform: &eUNKNOWN"));
            sender.sendMessage(messages.color("&7Detector: &eNOT INITIALIZED"));
            sender.sendMessage(messages.color("&8&m----------------------------------------"));
            return;
        }

        ClientPlatform platform = platformResolver.resolve(target);
        String color = switch (platform) {
            case JAVA -> "&a";
            case BEDROCK -> "&b";
            case UNKNOWN -> "&e";
        };
        sender.sendMessage(messages.color("&7Platform: " + color + platform.name()));
        sender.sendMessage(messages.color("&7Detector: &f" + platformResolver.statusName()));
        sender.sendMessage(messages.color("&7Geyser: &f" + platformResolver.geyserVersion()
                + (platformResolver.isGeyserHookActive() ? " &a[ACTIVE]" : " &7[INACTIVE]")));
        sender.sendMessage(messages.color("&7Floodgate: &f" + platformResolver.floodgateVersion()
                + (platformResolver.isFloodgateHookActive() ? " &a[ACTIVE]" : " &7[INACTIVE]")));
        if (poseEngine != null) {
            sender.sendMessage(messages.color("&7KO pose mode: &f" + poseEngine.modeName(target)));
        }
        sender.sendMessage(messages.color("&8&m----------------------------------------"));
    }

    private void handleCompatibility(CommandSender sender) {
        sender.sendMessage(messages.color("&8&m----------------------------------------"));
        sender.sendMessage(messages.color("&c&lCdrKnockout &7- &fCompatibility"));

        if (axGravesCompatibility == null) {
            sender.sendMessage(messages.color("&7AxGraves: &eNOT INITIALIZED"));
        } else {
            String status = axGravesCompatibility.statusName();
            String statusColor = status.equals("ACTIVE") ? "&a" : status.equals("NOT_INSTALLED") ? "&7" : "&e";
            sender.sendMessage(messages.color("&7AxGraves: " + statusColor + status));
            sender.sendMessage(messages.color("&7AxGraves version: &f" + axGravesCompatibility.detectedVersion()));
            sender.sendMessage(messages.color("&7Pre-spawn hook: "
                    + (axGravesCompatibility.isHookActive() ? "&aACTIVE" : "&eINACTIVE")));
            sender.sendMessage(messages.color("&7Blocked KO graves: &f" + axGravesCompatibility.blockedKnockedGraves()));
            sender.sendMessage(messages.color("&7Blocked duplicate graves: &f" + axGravesCompatibility.blockedDuplicateGraves()));
            if (!axGravesCompatibility.hookFailure().isBlank()) {
                sender.sendMessage(messages.color("&7AxGraves hook detail: &c" + axGravesCompatibility.hookFailure()));
            }
        }

        if (platformResolver == null) {
            sender.sendMessage(messages.color("&7Client detector: &eNOT INITIALIZED"));
        } else {
            sender.sendMessage(messages.color("&7Client detector: &f" + platformResolver.statusName()));
            sender.sendMessage(messages.color("&7Geyser version: &f" + platformResolver.geyserVersion()));
            sender.sendMessage(messages.color("&7Floodgate version: &f" + platformResolver.floodgateVersion()));
            if (!platformResolver.geyserFailure().isBlank()) {
                sender.sendMessage(messages.color("&7Geyser hook detail: &c" + platformResolver.geyserFailure()));
            }
            if (!platformResolver.floodgateFailure().isBlank()) {
                sender.sendMessage(messages.color("&7Floodgate hook detail: &c" + platformResolver.floodgateFailure()));
            }
        }

        sender.sendMessage(messages.color("&8&m----------------------------------------"));
    }

    private void handleDoctor(CommandSender sender) {
        if (diagnostics == null) {
            sender.sendMessage(messages.color("&8[&cCdrKnockout&8] &eProduction diagnostics are not initialized."));
            return;
        }
        diagnostics.send(sender);
    }

    private boolean checkPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(messages.format("no-permission"));
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2
                && !args[0].equalsIgnoreCase("reload")
                && !args[0].equalsIgnoreCase("compat")
                && !args[0].equalsIgnoreCase("doctor")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
