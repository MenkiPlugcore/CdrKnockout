package dev.cadera.cdrknockout.util;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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

        try (InputStream input = plugin.getResource("messages.yml")) {
            if (input != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(input, StandardCharsets.UTF_8)
                );
                config.setDefaults(defaults);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Unable to load default messages: " + exception.getMessage());
        }
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
