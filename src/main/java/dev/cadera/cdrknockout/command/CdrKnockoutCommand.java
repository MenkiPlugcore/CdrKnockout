package dev.cadera.cdrknockout.command;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.core.KnockoutManager;
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

    private static final List<String> SUBCOMMANDS = List.of("reload", "knockout", "revive", "kill", "status");

    private final CdrKnockoutPlugin plugin;
    private final KnockoutManager manager;
    private final Messages messages;

    public CdrKnockoutCommand(CdrKnockoutPlugin plugin, KnockoutManager manager, Messages messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
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
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
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
