package dev.cadera.cdrknockout.gameplay;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.api.event.CdrKnockoutDeathEvent;
import dev.cadera.cdrknockout.api.event.CdrKnockoutEvent;
import dev.cadera.cdrknockout.api.event.CdrRevivedEvent;
import dev.cadera.cdrknockout.util.AtomicYamlStorage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class StatisticsManager implements Listener {

    private final CdrKnockoutPlugin plugin;
    private final File file;
    private final Map<UUID, MutableStats> stats = new LinkedHashMap<>();
    private BukkitTask autosaveTask;
    private boolean dirty;
    private boolean runtimeEnabled;

    public StatisticsManager(CdrKnockoutPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "statistics.yml");
    }

    public void start() {
        shutdownTask();
        stats.clear();
        dirty = false;
        runtimeEnabled = enabled();
        if (!runtimeEnabled) {
            return;
        }
        load();
        scheduleAutosave();
    }

    public void reload() {
        shutdownTask();
        boolean nextEnabled = enabled();

        if (!nextEnabled) {
            if (runtimeEnabled && dirty) {
                save();
            }
            stats.clear();
            dirty = false;
            runtimeEnabled = false;
            return;
        }

        if (!runtimeEnabled) {
            stats.clear();
            dirty = false;
            load();
        }
        runtimeEnabled = true;
        scheduleAutosave();
    }

    public void shutdown() {
        shutdownTask();
        if (runtimeEnabled && dirty) {
            save();
        }
        runtimeEnabled = false;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("gameplay.statistics.enabled", true);
    }

    public StatsSnapshot snapshot(Player player) {
        return player == null ? StatsSnapshot.EMPTY : snapshot(player.getUniqueId(), player.getName());
    }

    public StatsSnapshot snapshot(UUID uuid, String fallbackName) {
        MutableStats value = stats.get(uuid);
        if (value == null) {
            return new StatsSnapshot(uuid, fallbackName == null ? "unknown" : fallbackName, 0, 0, 0, 0, 0, 0);
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            value.lastKnownName = fallbackName;
        }
        return value.snapshot(uuid);
    }

    public void recordSelfRevive(Player player) {
        mutate(player, value -> value.selfRevives++);
    }

    public void recordDistress(Player player) {
        mutate(player, value -> value.distressSignals++);
    }

    public void recordMedicalKitUse(Player player) {
        mutate(player, value -> value.medicalKitUses++);
    }

    @EventHandler
    public void onKnockout(CdrKnockoutEvent event) {
        if (!event.isRecovered()) {
            mutate(event.getPlayer(), value -> value.knockouts++);
        }
    }

    @EventHandler
    public void onRevived(CdrRevivedEvent event) {
        mutate(event.getPlayer(), value -> value.revivesReceived++);
    }

    @EventHandler
    public void onKnockoutDeath(CdrKnockoutDeathEvent event) {
        mutate(event.getPlayer(), value -> value.knockoutDeaths++);
    }

    private void mutate(Player player, java.util.function.Consumer<MutableStats> mutation) {
        if (!enabled() || player == null) {
            return;
        }
        MutableStats value = stats.computeIfAbsent(player.getUniqueId(), ignored -> new MutableStats());
        value.lastKnownName = player.getName();
        mutation.accept(value);
        dirty = true;
    }

    private void scheduleAutosave() {
        long ticks = Math.max(40L, plugin.getConfig().getLong("gameplay.statistics.autosave-ticks", 200L));
        autosaveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flushIfDirty, ticks, ticks);
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                MutableStats value = new MutableStats();
                value.lastKnownName = section.getString("name", "unknown");
                value.knockouts = Math.max(0L, section.getLong("knockouts"));
                value.revivesReceived = Math.max(0L, section.getLong("revives-received"));
                value.knockoutDeaths = Math.max(0L, section.getLong("knockout-deaths"));
                value.selfRevives = Math.max(0L, section.getLong("self-revives"));
                value.distressSignals = Math.max(0L, section.getLong("distress-signals"));
                value.medicalKitUses = Math.max(0L, section.getLong("medical-kit-uses"));
                stats.put(uuid, value);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping invalid statistics UUID: " + key);
            }
        }
    }

    private void flushIfDirty() {
        if (!runtimeEnabled || !dirty) {
            return;
        }
        save();
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 2);
        for (Map.Entry<UUID, MutableStats> entry : stats.entrySet()) {
            String base = "players." + entry.getKey();
            MutableStats value = entry.getValue();
            yaml.set(base + ".name", value.lastKnownName);
            yaml.set(base + ".knockouts", value.knockouts);
            yaml.set(base + ".revives-received", value.revivesReceived);
            yaml.set(base + ".knockout-deaths", value.knockoutDeaths);
            yaml.set(base + ".self-revives", value.selfRevives);
            yaml.set(base + ".distress-signals", value.distressSignals);
            yaml.set(base + ".medical-kit-uses", value.medicalKitUses);
        }
        if (AtomicYamlStorage.save(yaml, file, plugin.getLogger())) {
            dirty = false;
        }
    }

    private void shutdownTask() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
    }

    private static final class MutableStats {
        private String lastKnownName = "unknown";
        private long knockouts;
        private long revivesReceived;
        private long knockoutDeaths;
        private long selfRevives;
        private long distressSignals;
        private long medicalKitUses;

        private StatsSnapshot snapshot(UUID uuid) {
            return new StatsSnapshot(uuid, lastKnownName, knockouts, revivesReceived, knockoutDeaths,
                    selfRevives, distressSignals, medicalKitUses);
        }
    }

    public record StatsSnapshot(
            UUID playerId,
            String playerName,
            long knockouts,
            long revivesReceived,
            long knockoutDeaths,
            long selfRevives,
            long distressSignals,
            long medicalKitUses
    ) {
        public static final StatsSnapshot EMPTY = new StatsSnapshot(new UUID(0L, 0L), "unknown", 0, 0, 0, 0, 0, 0);
    }
}
