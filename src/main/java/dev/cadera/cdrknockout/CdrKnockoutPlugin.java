package dev.cadera.cdrknockout;

import dev.cadera.cdrknockout.api.CdrKnockoutApi;
import dev.cadera.cdrknockout.api.internal.CdrKnockoutApiImpl;
import dev.cadera.cdrknockout.api.internal.CdrKnockoutEventBridge;
import dev.cadera.cdrknockout.command.CdrKnockoutCommand;
import dev.cadera.cdrknockout.command.GiveUpCommand;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.core.KnockoutPersistence;
import dev.cadera.cdrknockout.execution.ExecutionManager;
import dev.cadera.cdrknockout.integration.AxGravesCompatibility;
import dev.cadera.cdrknockout.integration.PlaceholderApiIntegration;
import dev.cadera.cdrknockout.listener.KnockoutListener;
import dev.cadera.cdrknockout.platform.ClientPlatformResolver;
import dev.cadera.cdrknockout.platform.PoseEngine;
import dev.cadera.cdrknockout.revive.ReviveManager;
import dev.cadera.cdrknockout.util.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class CdrKnockoutPlugin extends JavaPlugin {

    private Messages messages;
    private KnockoutPersistence persistence;
    private ClientPlatformResolver platformResolver;
    private PoseEngine poseEngine;
    private KnockoutManager knockoutManager;
    private ReviveManager reviveManager;
    private ExecutionManager executionManager;
    private AxGravesCompatibility axGravesCompatibility;
    private CdrKnockoutApi cdrKnockoutApi;
    private CdrKnockoutEventBridge apiEventBridge;
    private PlaceholderApiIntegration placeholderApiIntegration;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        messages.reload();

        platformResolver = new ClientPlatformResolver(this);
        platformResolver.start();
        poseEngine = new PoseEngine(this, platformResolver);

        persistence = new KnockoutPersistence(this);
        persistence.start();

        knockoutManager = new KnockoutManager(this, messages, persistence, poseEngine);
        reviveManager = new ReviveManager(this, knockoutManager, messages);
        executionManager = new ExecutionManager(this, knockoutManager, reviveManager, messages);
        reviveManager.setExecutionManager(executionManager);
        knockoutManager.setReviveManager(reviveManager);
        axGravesCompatibility = new AxGravesCompatibility(this, knockoutManager);

        knockoutManager.start();
        reviveManager.start();
        executionManager.start();
        axGravesCompatibility.start();

        getServer().getPluginManager().registerEvents(
                new KnockoutListener(this, knockoutManager, reviveManager, executionManager, messages), this
        );

        cdrKnockoutApi = new CdrKnockoutApiImpl(
                this,
                knockoutManager,
                reviveManager,
                executionManager,
                platformResolver,
                poseEngine
        );
        getServer().getServicesManager().register(
                CdrKnockoutApi.class,
                cdrKnockoutApi,
                this,
                ServicePriority.Normal
        );

        apiEventBridge = new CdrKnockoutEventBridge(this, knockoutManager);
        getServer().getPluginManager().registerEvents(apiEventBridge, this);
        apiEventBridge.start();

        placeholderApiIntegration = new PlaceholderApiIntegration(this, cdrKnockoutApi);
        placeholderApiIntegration.start();

        CdrKnockoutCommand commandHandler = new CdrKnockoutCommand(
                this,
                knockoutManager,
                messages,
                axGravesCompatibility,
                platformResolver,
                poseEngine
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
        if (placeholderApiIntegration != null) {
            placeholderApiIntegration.shutdown();
        }
        if (apiEventBridge != null) {
            apiEventBridge.shutdown();
        }
        getServer().getServicesManager().unregisterAll(this);

        if (axGravesCompatibility != null) {
            axGravesCompatibility.shutdown();
        }
        if (executionManager != null) {
            executionManager.shutdown();
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
        if (platformResolver != null) {
            platformResolver.shutdown();
        }
    }

    public void reloadRuntimeConfig() {
        reloadConfig();
        messages.reload();
        if (platformResolver != null) {
            platformResolver.reload();
        }
        if (persistence != null) {
            persistence.reload();
        }
        if (executionManager != null) {
            executionManager.onReload();
        }
        reviveManager.onReload();
        knockoutManager.onReload();
        axGravesCompatibility.reload();
        if (apiEventBridge != null) {
            apiEventBridge.reload();
        }
        if (placeholderApiIntegration != null) {
            placeholderApiIntegration.reload();
        }
    }

    public CdrKnockoutApi api() {
        return cdrKnockoutApi;
    }
}
