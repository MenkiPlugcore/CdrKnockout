package dev.cadera.cdrknockout.integration;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.api.CdrKnockoutApi;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Loads the actual PlaceholderAPI expansion only when PlaceholderAPI is present,
 * preventing Java-only installations from resolving PlaceholderAPI classes.
 */
public final class PlaceholderApiIntegration {

    private final CdrKnockoutPlugin plugin;
    private final CdrKnockoutApi api;
    private Object expansion;
    private String status = "NOT_INSTALLED";
    private String failure = "";

    public PlaceholderApiIntegration(CdrKnockoutPlugin plugin, CdrKnockoutApi api) {
        this.plugin = plugin;
        this.api = api;
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
            Object created = expansionClass
                    .getConstructor(CdrKnockoutPlugin.class, CdrKnockoutApi.class)
                    .newInstance(plugin, api);
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
                Method unregister = expansion.getClass().getMethod("unregister");
                unregister.invoke(expansion);
            } catch (Throwable ignored) {
                // Best-effort cleanup; PlaceholderAPI also clears expansions on plugin disable.
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
