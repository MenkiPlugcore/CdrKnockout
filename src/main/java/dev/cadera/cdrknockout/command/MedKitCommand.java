package dev.cadera.cdrknockout.command;

import dev.cadera.cdrknockout.gameplay.MedicalKitItems;
import dev.cadera.cdrknockout.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class MedKitCommand implements CommandExecutor {

    private final MedicalKitItems medicalKitItems;
    private final Messages messages;

    public MedKitCommand(MedicalKitItems medicalKitItems, Messages messages) {
        this.medicalKitItems = medicalKitItems;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("cdrknockout.medkit.give")) {
            sender.sendMessage(messages.format("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.color("&cGunakan: /medkit <player> [amount]"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messages.format("player-not-found", "%player%", args[0]));
            return true;
        }
        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                sender.sendMessage(messages.color("&cAmount harus berupa angka 1-64."));
                return true;
            }
        }

        ItemStack item = medicalKitItems.create(amount);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
        for (ItemStack value : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), value);
        }
        sender.sendMessage(messages.format("medkit-given", "%player%", target.getName(), "%amount%", Integer.toString(amount)));
        target.sendMessage(messages.format("medkit-received", "%amount%", Integer.toString(amount)));
        return true;
    }
}
