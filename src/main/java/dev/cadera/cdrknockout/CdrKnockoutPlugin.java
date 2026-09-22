package dev.cadera.cdrknockout;

import dev.cadera.cdrknockout.command.CdrKnockoutCommand;
import dev.cadera.cdrknockout.command.GiveUpCommand;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.core.KnockoutPersistence;
import dev.cadera.cdrknockout.integration.AxGravesCompatibility;
import dev.cadera.cdrknockout.listener.KnockoutListener;
import dev.cadera.cdrknockout.revive.ReviveManager;
import dev.cadera.cdrknockout.util.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CdrKnockoutPlugin extends JavaPlugin {

    private Messages messages;
    private KnockoutPersistence persistence;
    private KnockoutManager knockoutManager;
    private ReviveManager reviveManager;
    private AxGravesCompatibility axGravesCompatibility;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        messages.reload();

        persistence = new KnockoutPersistence(this);
        persistence.start();

        knockoutManager = new KnockoutManager(this, messages, persistence);
        reviveManager = new ReviveManager(this, knockoutManager, messages);
        knockoutManager.setReviveManager(reviveManager);
        axGravesCompatibility = new AxGravesCompatibility(this, knockoutManager);

        knockoutManager.start();
        reviveManager.start();
        axGravesCompatibility.start();

        getServer().getPluginManager().registerEvents(
                new KnockoutListener(this, knockoutManager, reviveManager, messages), this
        );

        CdrKnockoutCommand commandHandler = new CdrKnockoutCommand(
                this,
                knockoutManager,
                messages,
                axGravesCompatibility
        );
        PluginCommand command = getCommand("cdrko");
        if (command == null) {
            throw new IllegalStateException("Command cdrko is missing from plugin.yml");
        }
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        PluginCommand giveUp = getCommand("giveup");
        if (giveUp == null) {
            throw new IllegalStateException("Command giveup is missing from plugin.yml");
        }
        giveUp.setExecutor(new GiveUpCommand(this, knockoutManager, messages));

        getLogger().info("CdrKnockout v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (axGravesCompatibility != null) {
            axGravesCompatibility.shutdown();
        }
        if (reviveManager != null) {
            reviveManager.shutdown();
        }
        if (knockoutManager != null) {
            knockoutManager.shutdown();
        }
        if (persistence != null) {
            persistence.shutdown();
        }
    }

    public void reloadRuntimeConfig() {
        reloadConfig();
        messages.reload();
        if (persistence != null) {
            persistence.reload();
        }
        reviveManager.onReload();
        knockoutManager.onReload();
        axGravesCompatibility.reload();
    }
}
