package dev.cadera.cdrknockout;

import dev.cadera.cdrknockout.command.CdrKnockoutCommand;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.listener.KnockoutListener;
import dev.cadera.cdrknockout.util.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CdrKnockoutPlugin extends JavaPlugin {

    private Messages messages;
    private KnockoutManager knockoutManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        messages.reload();

        knockoutManager = new KnockoutManager(this, messages);
        knockoutManager.start();

        getServer().getPluginManager().registerEvents(
                new KnockoutListener(this, knockoutManager, messages), this
        );

        CdrKnockoutCommand commandHandler = new CdrKnockoutCommand(this, knockoutManager, messages);
        PluginCommand command = getCommand("cdrko");
        if (command == null) {
            throw new IllegalStateException("Command cdrko is missing from plugin.yml");
        }
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getLogger().info("CdrKnockout v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (knockoutManager != null) {
            knockoutManager.shutdown();
        }
    }

    public void reloadRuntimeConfig() {
        reloadConfig();
        messages.reload();
        knockoutManager.onReload();
    }
}
