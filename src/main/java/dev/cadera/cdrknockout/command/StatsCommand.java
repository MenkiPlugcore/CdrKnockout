package dev.cadera.cdrknockout.command;

import dev.cadera.cdrknockout.gameplay.StatisticsManager;
import dev.cadera.cdrknockout.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class StatsCommand implements CommandExecutor {

    private final StatisticsManager statistics;
    private final Messages messages;

    public StatsCommand(StatisticsManager statistics, Messages messages) {
        this.statistics = statistics;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("cdrknockout.stats")) {
            sender.sendMessage(messages.format("no-permission"));
            return true;
        }

        OfflinePlayer target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.color("&cGunakan: /kostats <player>"));
                return true;
            }
            target = player;
        } else {
            if (!sender.hasPermission("cdrknockout.stats.others")) {
                sender.sendMessage(messages.format("no-permission"));
                return true;
            }
            target = Bukkit.getOfflinePlayer(args[0]);
        }

        StatisticsManager.StatsSnapshot stats = statistics.snapshot(target.getUniqueId(), target.getName());
        sender.sendMessage(messages.color("&8&m----------------------------------------"));
        sender.sendMessage(messages.color("&c&lCdrKnockout &7- &fStatistics"));
        sender.sendMessage(messages.color("&7Player: &f" + stats.playerName()));
        sender.sendMessage(messages.color("&7Knockouts: &f" + stats.knockouts()));
        sender.sendMessage(messages.color("&7Revives received: &f" + stats.revivesReceived()));
        sender.sendMessage(messages.color("&7Deaths after KO: &f" + stats.knockoutDeaths()));
        sender.sendMessage(messages.color("&7Self revives: &f" + stats.selfRevives()));
        sender.sendMessage(messages.color("&7Distress signals: &f" + stats.distressSignals()));
        sender.sendMessage(messages.color("&7Medical kits used: &f" + stats.medicalKitUses()));
        sender.sendMessage(messages.color("&8&m----------------------------------------"));
        return true;
    }
}
