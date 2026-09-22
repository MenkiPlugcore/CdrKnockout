package dev.cadera.cdrknockout.integration;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.core.KnockoutManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runtime-only AxGraves integration.
 *
 * <p>No AxGraves classes are linked at compile time. If AxGraves is present,
 * its GravePreSpawnEvent is resolved through the AxGraves plugin classloader.
 * This keeps CdrKnockout usable without AxGraves while still allowing a hard
 * guard against graves spawning during the KNOCKED state.</p>
 */
public final class AxGravesCompatibility {

    private static final String AXGRAVES_PLUGIN = "AxGraves";
    private static final String PRE_SPAWN_EVENT = "com.artillexstudios.axgraves.api.events.GravePreSpawnEvent";

    private final CdrKnockoutPlugin plugin;
    private final KnockoutManager knockoutManager;
    private final Set<UUID> managedDeathGraveSeen = ConcurrentHashMap.newKeySet();
    private final AtomicLong blockedKnockedGraves = new AtomicLong();
    private final AtomicLong blockedDuplicateGraves = new AtomicLong();

    private Listener listener;
    private volatile boolean hookActive;
    private volatile String detectedVersion = "not-installed";
    private volatile String hookFailure = "";

    public AxGravesCompatibility(CdrKnockoutPlugin plugin, KnockoutManager knockoutManager) {
        this.plugin = plugin;
        this.knockoutManager = knockoutManager;
    }

    public void start() {
        tryHook();
    }

    public void reload() {
        if (isEnabled() && !hookActive) {
            tryHook();
        }
    }

    public void shutdown() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        hookActive = false;
        managedDeathGraveSeen.clear();
    }

    public boolean isInstalled() {
        return plugin.getServer().getPluginManager().getPlugin(AXGRAVES_PLUGIN) != null;
    }

    public boolean isHookActive() {
        return hookActive;
    }

    public String detectedVersion() {
        return detectedVersion;
    }

    public long blockedKnockedGraves() {
        return blockedKnockedGraves.get();
    }

    public long blockedDuplicateGraves() {
        return blockedDuplicateGraves.get();
    }

    public String hookFailure() {
        return hookFailure;
    }

    public String statusName() {
        if (!isEnabled()) {
            return "DISABLED";
        }
        if (!isInstalled()) {
            return "NOT_INSTALLED";
        }
        return hookActive ? "ACTIVE" : "NATURAL_ONLY";
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("compatibility.axgraves.enabled", true);
    }

    private void tryHook() {
        if (!isEnabled() || hookActive) {
            return;
        }

        Plugin axGraves = plugin.getServer().getPluginManager().getPlugin(AXGRAVES_PLUGIN);
        if (axGraves == null) {
            detectedVersion = "not-installed";
            hookFailure = "";
            debug("AxGraves not installed; natural PlayerDeathEvent compatibility remains available.");
            return;
        }

        detectedVersion = axGraves.getDescription().getVersion();

        try {
            ClassLoader classLoader = axGraves.getClass().getClassLoader();
            Class<?> rawEventClass = classLoader.loadClass(PRE_SPAWN_EVENT);
            Class<? extends Event> eventClass = rawEventClass.asSubclass(Event.class);
            Method getPlayer = rawEventClass.getMethod("getPlayer");

            Listener localListener = new Listener() {
            };
            EventExecutor executor = (ignored, event) -> handleGravePreSpawn(event, getPlayer);

            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    localListener,
                    EventPriority.HIGHEST,
                    executor,
                    plugin,
                    false
            );

            listener = localListener;
            hookActive = true;
            hookFailure = "";
            plugin.getLogger().info("AxGraves compatibility active. Detected AxGraves v" + detectedVersion + ".");
        } catch (Throwable throwable) {
            hookActive = false;
            hookFailure = throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage());
            plugin.getLogger().warning(
                    "AxGraves detected (v" + detectedVersion + ") but GravePreSpawnEvent hook could not be installed. "
                            + "Natural PlayerDeathEvent compatibility is still used. Cause: " + hookFailure
            );
        }
    }

    private void handleGravePreSpawn(Event event, Method getPlayer) {
        if (!isEnabled()) {
            return;
        }

        try {
            if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                return;
            }

            Object playerObject = getPlayer.invoke(event);
            if (!(playerObject instanceof Player player)) {
                return;
            }

            UUID playerId = player.getUniqueId();

            // A KNOCKED player has not died yet. A grave at this stage is always invalid.
            if (knockoutManager.isKnocked(player) && !knockoutManager.isDeathInProgress(player)) {
                if (plugin.getConfig().getBoolean("compatibility.axgraves.block-graves-while-knocked", true)
                        && event instanceof Cancellable cancellable) {
                    cancellable.setCancelled(true);
                    blockedKnockedGraves.incrementAndGet();
                    diagnostic("Blocked AxGraves grave while KNOCKED for " + player.getName());
                }
                return;
            }

            // During a CdrKnockout managed real-death pass-through, accept only one grave pre-spawn.
            if (knockoutManager.isDeathInProgress(player)
                    && plugin.getConfig().getBoolean("compatibility.axgraves.prevent-duplicate-managed-graves", true)) {
                if (!managedDeathGraveSeen.add(playerId)) {
                    if (event instanceof Cancellable cancellable) {
                        cancellable.setCancelled(true);
                        blockedDuplicateGraves.incrementAndGet();
                        diagnostic("Blocked duplicate AxGraves grave for managed death of " + player.getName());
                    }
                    return;
                }

                long cleanupTicks = Math.max(
                        2L,
                        plugin.getConfig().getLong("compatibility.axgraves.duplicate-window-ticks", 40L)
                );
                plugin.getServer().getScheduler().runTaskLater(
                        plugin,
                        () -> managedDeathGraveSeen.remove(playerId),
                        cleanupTicks
                );
                diagnostic("Allowed AxGraves grave for managed real death of " + player.getName());
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("AxGraves compatibility event handling failed: " + throwable.getMessage());
        }
    }

    private void diagnostic(String message) {
        if (plugin.getConfig().getBoolean("compatibility.axgraves.log-diagnostics", false)) {
            plugin.getLogger().info("[AxGravesCompat] " + message);
        }
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] [AxGravesCompat] " + message);
        }
    }
}
