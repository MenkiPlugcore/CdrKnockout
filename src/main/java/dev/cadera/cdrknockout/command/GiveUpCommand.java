package dev.cadera.cdrknockout.command;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class GiveUpCommand implements CommandExecutor {

    private final CdrKnockoutPlugin plugin;
    private final KnockoutManager manager;
    private final Messages messages;

    public GiveUpCommand(CdrKnockoutPlugin plugin, KnockoutManager manager, Messages messages) {
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("giveup-player-only"));
            return true;
        }
        if (!player.hasPermission("cdrknockout.giveup")) {
            player.sendMessage(messages.format("no-permission"));
            return true;
        }
        if (!plugin.getConfig().getBoolean("knockout.bleedout.giveup.enabled", true)) {
            player.sendMessage(messages.format("giveup-disabled"));
            return true;
        }
        if (!manager.isKnocked(player)) {
            player.sendMessage(messages.format("giveup-not-knocked"));
            return true;
        }
        if (!manager.giveUp(player)) {
            player.sendMessage(messages.format("giveup-failed"));
        }
        return true;
    }
}
