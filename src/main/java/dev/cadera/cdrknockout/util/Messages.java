package dev.cadera.cdrknockout.util;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public final class Messages {

    private final CdrKnockoutPlugin plugin;
    private YamlConfiguration config;

    public Messages(CdrKnockoutPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String format(String key, String... replacements) {
        String raw = config.getString(key, key);
        String prefix = config.getString("prefix", "&8[&cCdrKnockout&8] ");
        raw = raw.replace("%prefix%", prefix);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return color(raw);
    }

    public String color(String value) {
        if (value == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
