package dev.cadera.cdrknockout.integration;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.api.CdrKnockoutApi;
import dev.cadera.cdrknockout.gameplay.DistressManager;
import dev.cadera.cdrknockout.gameplay.SelfReviveManager;
import dev.cadera.cdrknockout.gameplay.StatisticsManager;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public final class PlaceholderApiIntegration {

    private final CdrKnockoutPlugin plugin;
    private final CdrKnockoutApi api;
    private final StatisticsManager statistics;
    private final SelfReviveManager selfReviveManager;
    private final DistressManager distressManager;
    private Object expansion;
    private String status = "NOT_INSTALLED";
    private String failure = "";

    public PlaceholderApiIntegration(
            CdrKnockoutPlugin plugin,
            CdrKnockoutApi api,
            StatisticsManager statistics,
            SelfReviveManager selfReviveManager,
            DistressManager distressManager
    ) {
        this.plugin = plugin;
        this.api = api;
        this.statistics = statistics;
        this.selfReviveManager = selfReviveManager;
        this.distressManager = distressManager;
    }

    public void start() {
        shutdown();
        failure = "";
        if (!plugin.getConfig().getBoolean("placeholderapi.enabled", true)) {
            status = "DISABLED";
            return;
        }
        Plugin papi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            status = "NOT_INSTALLED";
            return;
        }
        try {
            Class<?> expansionClass = Class.forName(
                    "dev.cadera.cdrknockout.integration.papi.CdrKnockoutPlaceholderExpansion",
                    true,
                    plugin.getClass().getClassLoader()
            );
            Object created = expansionClass.getConstructor(
                    CdrKnockoutPlugin.class,
                    CdrKnockoutApi.class,
                    StatisticsManager.class,
                    SelfReviveManager.class,
                    DistressManager.class
            ).newInstance(plugin, api, statistics, selfReviveManager, distressManager);
            Method register = expansionClass.getMethod("register");
            Object registered = register.invoke(created);
            if (registered instanceof Boolean value && !value) {
                throw new IllegalStateException("PlaceholderExpansion.register() returned false");
            }
            expansion = created;
            status = "ACTIVE";
            plugin.getLogger().info("PlaceholderAPI expansion registered: %cdrknockout_*%");
        } catch (Throwable throwable) {
            expansion = null;
            status = "FAILED";
            failure = throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage());
            plugin.getLogger().warning("PlaceholderAPI integration failed: " + failure);
        }
    }

    public void reload() {
        start();
    }

    public void shutdown() {
        if (expansion != null) {
            try {
                expansion.getClass().getMethod("unregister").invoke(expansion);
            } catch (Throwable ignored) {
            }
        }
        expansion = null;
    }

    public String statusName() {
        return status;
    }

    public String failure() {
        return failure;
    }
}
