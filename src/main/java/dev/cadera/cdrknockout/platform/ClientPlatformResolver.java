package dev.cadera.cdrknockout.platform;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Detects Java/Bedrock clients without a hard dependency on Geyser or Floodgate.
 *
 * <p>Detection order is Geyser API first, then Floodgate API. Both are resolved
 * reflectively from the owning plugin classloader so CdrKnockout can still run
 * on pure Java servers.</p>
 */
public final class ClientPlatformResolver {

    private static final String GEYSER_API_CLASS = "org.geysermc.geyser.api.GeyserApi";
    private static final String FLOODGATE_API_CLASS = "org.geysermc.floodgate.api.FloodgateApi";

    private final CdrKnockoutPlugin plugin;

    private Object geyserApi;
    private Method geyserIsBedrockPlayer;
    private Object floodgateApi;
    private Method floodgateIsFloodgatePlayer;

    private String geyserVersion = "not-installed";
    private String floodgateVersion = "not-installed";
    private String geyserFailure = "";
    private String floodgateFailure = "";

    public ClientPlatformResolver(CdrKnockoutPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        refreshHooks();
    }

    public void reload() {
        refreshHooks();
    }

    public void shutdown() {
        geyserApi = null;
        geyserIsBedrockPlayer = null;
        floodgateApi = null;
        floodgateIsFloodgatePlayer = null;
    }

    public ClientPlatform resolve(Player player) {
        if (player == null) {
            return ClientPlatform.UNKNOWN;
        }
        return resolve(player.getUniqueId());
    }

    public ClientPlatform resolve(UUID uuid) {
        if (uuid == null) {
            return ClientPlatform.UNKNOWN;
        }

        Boolean geyserResult = queryGeyser(uuid);
        if (Boolean.TRUE.equals(geyserResult)) {
            return ClientPlatform.BEDROCK;
        }

        Boolean floodgateResult = queryFloodgate(uuid);
        if (Boolean.TRUE.equals(floodgateResult)) {
            return ClientPlatform.BEDROCK;
        }

        if (geyserResult != null || floodgateResult != null) {
            return ClientPlatform.JAVA;
        }

        return ClientPlatform.UNKNOWN;
    }

    public boolean isBedrock(Player player) {
        return resolve(player) == ClientPlatform.BEDROCK;
    }

    public boolean isGeyserHookActive() {
        return geyserApi != null && geyserIsBedrockPlayer != null;
    }

    public boolean isFloodgateHookActive() {
        return floodgateApi != null && floodgateIsFloodgatePlayer != null;
    }

    public String geyserVersion() {
        return geyserVersion;
    }

    public String floodgateVersion() {
        return floodgateVersion;
    }

    public String geyserFailure() {
        return geyserFailure;
    }

    public String floodgateFailure() {
        return floodgateFailure;
    }

    public String statusName() {
        if (isGeyserHookActive() && isFloodgateHookActive()) {
            return "GEYSER+FLOODGATE";
        }
        if (isGeyserHookActive()) {
            return "GEYSER";
        }
        if (isFloodgateHookActive()) {
            return "FLOODGATE";
        }
        return "NO_HOOK";
    }

    private void refreshHooks() {
        shutdown();
        geyserVersion = "not-installed";
        floodgateVersion = "not-installed";
        geyserFailure = "";
        floodgateFailure = "";

        if (!plugin.getConfig().getBoolean("compatibility.client.detection.enabled", true)) {
            return;
        }

        hookGeyser();
        hookFloodgate();

        if (plugin.getConfig().getBoolean("compatibility.client.detection.log-status", true)) {
            plugin.getLogger().info(
                    "Client platform detection: " + statusName()
                            + " (Geyser=" + geyserVersion
                            + ", Floodgate=" + floodgateVersion + ")"
            );
        }
    }

    private void hookGeyser() {
        Plugin geyser = firstPlugin("Geyser-Spigot", "Geyser");
        if (geyser == null) {
            return;
        }

        geyserVersion = geyser.getDescription().getVersion();
        try {
            Class<?> apiClass = geyser.getClass().getClassLoader().loadClass(GEYSER_API_CLASS);
            Method apiMethod = apiClass.getMethod("api");
            Object api = apiMethod.invoke(null);
            if (api == null) {
                throw new IllegalStateException("GeyserApi.api() returned null");
            }

            Method isBedrockPlayer = apiClass.getMethod("isBedrockPlayer", UUID.class);
            geyserApi = api;
            geyserIsBedrockPlayer = isBedrockPlayer;
        } catch (Throwable throwable) {
            geyserApi = null;
            geyserIsBedrockPlayer = null;
            geyserFailure = throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage());
            plugin.getLogger().warning("Geyser client detection hook unavailable: " + geyserFailure);
        }
    }

    private void hookFloodgate() {
        Plugin floodgate = firstPlugin("floodgate", "Floodgate");
        if (floodgate == null) {
            return;
        }

        floodgateVersion = floodgate.getDescription().getVersion();
        try {
            Class<?> apiClass = floodgate.getClass().getClassLoader().loadClass(FLOODGATE_API_CLASS);
            Method getInstance = apiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            if (api == null) {
                throw new IllegalStateException("FloodgateApi.getInstance() returned null");
            }

            Method isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            floodgateApi = api;
            floodgateIsFloodgatePlayer = isFloodgatePlayer;
        } catch (Throwable throwable) {
            floodgateApi = null;
            floodgateIsFloodgatePlayer = null;
            floodgateFailure = throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage());
            plugin.getLogger().warning("Floodgate client detection hook unavailable: " + floodgateFailure);
        }
    }

    private Boolean queryGeyser(UUID uuid) {
        if (!isGeyserHookActive()) {
            return null;
        }
        try {
            Object result = geyserIsBedrockPlayer.invoke(geyserApi, uuid);
            return result instanceof Boolean value ? value : null;
        } catch (Throwable throwable) {
            debug("Geyser player lookup failed for " + uuid + ": " + throwable.getMessage());
            return null;
        }
    }

    private Boolean queryFloodgate(UUID uuid) {
        if (!isFloodgateHookActive()) {
            return null;
        }
        try {
            Object result = floodgateIsFloodgatePlayer.invoke(floodgateApi, uuid);
            return result instanceof Boolean value ? value : null;
        } catch (Throwable throwable) {
            debug("Floodgate player lookup failed for " + uuid + ": " + throwable.getMessage());
            return null;
        }
    }

    private Plugin firstPlugin(String... names) {
        for (String name : names) {
            Plugin candidate = plugin.getServer().getPluginManager().getPlugin(name);
            if (candidate != null && candidate.isEnabled()) {
                return candidate;
            }
        }
        return null;
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] [ClientPlatform] " + message);
        }
    }
}
