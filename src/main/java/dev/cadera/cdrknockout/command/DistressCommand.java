package dev.cadera.cdrknockout.command;

import dev.cadera.cdrknockout.gameplay.DistressManager;
import dev.cadera.cdrknockout.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class DistressCommand implements CommandExecutor {

    private final DistressManager distressManager;
    private final Messages messages;

    public DistressCommand(DistressManager distressManager, Messages messages) {
        this.distressManager = distressManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.color("&cCommand ini hanya dapat digunakan player."));
            return true;
        }
        if (!player.hasPermission("cdrknockout.distress")) {
            player.sendMessage(messages.format("no-permission"));
            return true;
        }
        distressManager.send(player);
        return true;
    }
}
