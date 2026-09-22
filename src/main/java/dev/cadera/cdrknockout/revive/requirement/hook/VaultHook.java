package dev.cadera.cdrknockout.revive.requirement.hook;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

public final class VaultHook {

    private final CdrKnockoutPlugin plugin;
    private Object economyProvider;
    private Method hasMethod;
    private Method withdrawMethod;

    public VaultHook(CdrKnockoutPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void refresh() {
        economyProvider = null;
        hasMethod = null;
        withdrawMethod = null;

        try {
            Plugin vault = plugin.getServer().getPluginManager().getPlugin("Vault");
            if (vault == null || !vault.isEnabled()) {
                return;
            }

            Class<?> economyClass = Class.forName(
                    "net.milkbowl.vault.economy.Economy",
                    true,
                    vault.getClass().getClassLoader()
            );
            RegisteredServiceProvider registration = plugin.getServer()
                    .getServicesManager()
                    .getRegistration((Class) economyClass);
            if (registration == null || registration.getProvider() == null) {
                return;
            }

            economyProvider = registration.getProvider();
            hasMethod = economyClass.getMethod("has", OfflinePlayer.class, double.class);
            withdrawMethod = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
        } catch (ReflectiveOperationException | LinkageError exception) {
            debug("Vault hook unavailable: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            economyProvider = null;
            hasMethod = null;
            withdrawMethod = null;
        }
    }

    public boolean isAvailable() {
        return economyProvider != null && hasMethod != null && withdrawMethod != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (!isAvailable()) {
            return false;
        }
        try {
            Object result = hasMethod.invoke(economyProvider, player, amount);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException exception) {
            debug("Vault balance check failed: " + exception.getMessage());
            return false;
        }
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isAvailable()) {
            return false;
        }
        try {
            Object response = withdrawMethod.invoke(economyProvider, player, amount);
            if (response == null) {
                return false;
            }
            Method success = response.getClass().getMethod("transactionSuccess");
            return Boolean.TRUE.equals(success.invoke(response));
        } catch (ReflectiveOperationException exception) {
            debug("Vault withdraw failed: " + exception.getMessage());
            return false;
        }
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }
}
