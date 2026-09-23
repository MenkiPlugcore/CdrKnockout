package dev.cadera.cdrknockout.command;

import dev.cadera.cdrknockout.gameplay.SelfReviveManager;
import dev.cadera.cdrknockout.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SelfReviveCommand implements CommandExecutor {

    private final SelfReviveManager selfReviveManager;
    private final Messages messages;

    public SelfReviveCommand(SelfReviveManager selfReviveManager, Messages messages) {
        this.selfReviveManager = selfReviveManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.color("&cCommand ini hanya dapat digunakan player."));
            return true;
        }
        if (!player.hasPermission("cdrknockout.selfrevive")) {
            player.sendMessage(messages.format("no-permission"));
            return true;
        }
        selfReviveManager.manualStart(player);
        return true;
    }
}
