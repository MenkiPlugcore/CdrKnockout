package dev.cadera.cdrknockout;

import dev.cadera.cdrknockout.api.CdrKnockoutApi;
import dev.cadera.cdrknockout.api.internal.CdrKnockoutApiImpl;
import dev.cadera.cdrknockout.api.internal.CdrKnockoutEventBridge;
import dev.cadera.cdrknockout.command.CdrKnockoutCommand;
import dev.cadera.cdrknockout.command.DistressCommand;
import dev.cadera.cdrknockout.command.GiveUpCommand;
import dev.cadera.cdrknockout.command.MedKitCommand;
import dev.cadera.cdrknockout.command.SelfReviveCommand;
import dev.cadera.cdrknockout.command.StatsCommand;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.core.KnockoutPersistence;
import dev.cadera.cdrknockout.execution.ExecutionManager;
import dev.cadera.cdrknockout.gameplay.DistressManager;
import dev.cadera.cdrknockout.gameplay.MedicalKitItems;
import dev.cadera.cdrknockout.gameplay.SelfReviveManager;
import dev.cadera.cdrknockout.gameplay.StatisticsManager;
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
    private StatisticsManager statisticsManager;
    private SelfReviveManager selfReviveManager;
    private DistressManager distressManager;
    private MedicalKitItems medicalKitItems;

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

        statisticsManager = new StatisticsManager(this);
        statisticsManager.start();
        medicalKitItems = new MedicalKitItems(this);

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

        getServer().getPluginManager().registerEvents(statisticsManager, this);

        selfReviveManager = new SelfReviveManager(
                this,
                knockoutManager,
                reviveManager,
                executionManager,
                statisticsManager,
                messages
        );
        getServer().getPluginManager().registerEvents(selfReviveManager, this);
        selfReviveManager.start();

        distressManager = new DistressManager(this, knockoutManager, statisticsManager, messages);

        placeholderApiIntegration = new PlaceholderApiIntegration(
                this,
                cdrKnockoutApi,
                statisticsManager,
                selfReviveManager,
                distressManager
        );
        placeholderApiIntegration.start();

        CdrKnockoutCommand commandHandler = new CdrKnockoutCommand(
                this,
                knockoutManager,
                messages,
                axGravesCompatibility,
                platformResolver,
                poseEngine
        );
        registerCommand("cdrko", commandHandler);
        getCommand("cdrko").setTabCompleter(commandHandler);

        registerCommand("giveup", new GiveUpCommand(this, knockoutManager, messages));
        registerCommand("selfrevive", new SelfReviveCommand(selfReviveManager, messages));
        registerCommand("distress", new DistressCommand(distressManager, messages));
        registerCommand("kostats", new StatsCommand(statisticsManager, messages));
        registerCommand("medkit", new MedKitCommand(medicalKitItems, messages));

        getLogger().info("CdrKnockout v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (placeholderApiIntegration != null) {
            placeholderApiIntegration.shutdown();
        }
        if (selfReviveManager != null) {
            selfReviveManager.shutdown();
        }
        if (statisticsManager != null) {
            statisticsManager.shutdown();
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
        if (statisticsManager != null) {
            statisticsManager.reload();
        }
        if (executionManager != null) {
            executionManager.onReload();
        }
        if (reviveManager != null) {
            reviveManager.onReload();
        }
        if (selfReviveManager != null) {
            selfReviveManager.reload();
        }
        if (knockoutManager != null) {
            knockoutManager.onReload();
        }
        if (axGravesCompatibility != null) {
            axGravesCompatibility.reload();
        }
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

    public StatisticsManager statistics() {
        return statisticsManager;
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Command " + name + " is missing from plugin.yml");
        }
        command.setExecutor(executor);
    }
}
