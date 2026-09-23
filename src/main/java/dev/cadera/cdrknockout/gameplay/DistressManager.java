package dev.cadera.cdrknockout.gameplay;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.util.Messages;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DistressManager {

    private final CdrKnockoutPlugin plugin;
    private final KnockoutManager knockoutManager;
    private final StatisticsManager statistics;
    private final Messages messages;
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public DistressManager(
            CdrKnockoutPlugin plugin,
            KnockoutManager knockoutManager,
            StatisticsManager statistics,
            Messages messages
    ) {
        this.plugin = plugin;
        this.knockoutManager = knockoutManager;
        this.statistics = statistics;
        this.messages = messages;
    }

    public boolean send(Player sender) {
        if (sender == null) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("gameplay.distress.enabled", true)) {
            sender.sendMessage(messages.format("distress-disabled"));
            return false;
        }
        if (!knockoutManager.isKnocked(sender) || knockoutManager.isDeathInProgress(sender)) {
            sender.sendMessage(messages.format("distress-not-knocked"));
            return false;
        }
        long now = System.currentTimeMillis();
        long until = cooldownUntil.getOrDefault(sender.getUniqueId(), 0L);
        if (until > now) {
            sender.sendMessage(messages.format("distress-cooldown", "%time%", Long.toString((until - now + 999L) / 1000L)));
            return false;
        }

        double radius = Math.max(1.0D, plugin.getConfig().getDouble("gameplay.distress.radius", 64.0D));
        double radiusSquared = radius * radius;
        Location source = sender.getLocation();
        int receivers = 0;

        for (Player receiver : sender.getWorld().getPlayers()) {
            if (receiver.getUniqueId().equals(sender.getUniqueId()) || !receiver.isOnline() || receiver.isDead()) {
                continue;
            }
            if (plugin.getConfig().getBoolean("gameplay.distress.skip-knocked-receivers", true)
                    && knockoutManager.isKnocked(receiver)) {
                continue;
            }
            if (plugin.getConfig().getBoolean("gameplay.distress.require-receive-permission", false)
                    && !receiver.hasPermission("cdrknockout.distress.receive")) {
                continue;
            }
            double distanceSquared = receiver.getLocation().distanceSquared(source);
            if (distanceSquared > radiusSquared) {
                continue;
            }
            long distance = Math.round(Math.sqrt(distanceSquared));
            receiver.sendMessage(messages.format(
                    "distress-receiver",
                    "%player%", sender.getName(),
                    "%distance%", Long.toString(distance),
                    "%x%", Integer.toString(source.getBlockX()),
                    "%y%", Integer.toString(source.getBlockY()),
                    "%z%", Integer.toString(source.getBlockZ())
            ));
            playAlert(receiver);
            receivers++;
        }

        long cooldownSeconds = Math.max(0L, plugin.getConfig().getLong("gameplay.distress.cooldown-seconds", 15L));
        if (cooldownSeconds > 0L) {
            cooldownUntil.put(sender.getUniqueId(), now + cooldownSeconds * 1000L);
        }
        statistics.recordDistress(sender);
        sender.sendMessage(messages.format("distress-sent", "%count%", Integer.toString(receivers)));
        return true;
    }

    public long cooldownRemainingSeconds(Player player) {
        if (player == null) {
            return 0L;
        }
        long remaining = Math.max(0L, cooldownUntil.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis());
        return (remaining + 999L) / 1000L;
    }

    public void clear(Player player) {
        if (player != null) {
            cooldownUntil.remove(player.getUniqueId());
        }
    }

    private void playAlert(Player receiver) {
        if (!plugin.getConfig().getBoolean("gameplay.distress.sound.enabled", true)) {
            return;
        }
        String sound = plugin.getConfig().getString("gameplay.distress.sound.name", "minecraft:block.note_block.pling");
        float volume = (float) Math.max(0.0D, plugin.getConfig().getDouble("gameplay.distress.sound.volume", 0.8D));
        float pitch = (float) Math.max(0.0D, plugin.getConfig().getDouble("gameplay.distress.sound.pitch", 1.2D));
        if (sound != null && !sound.isBlank()) {
            receiver.playSound(receiver.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
        }
    }
}
