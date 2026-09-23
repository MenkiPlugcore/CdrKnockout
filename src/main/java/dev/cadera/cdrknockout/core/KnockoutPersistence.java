package dev.cadera.cdrknockout.core;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.util.AtomicYamlStorage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KnockoutPersistence {

    private final CdrKnockoutPlugin plugin;
    private final File file;
    private final Map<UUID, StoredSession> stored = new LinkedHashMap<>();
    private BukkitTask autosaveTask;
    private boolean dirty;
    private boolean runtimeEnabled;

    public KnockoutPersistence(CdrKnockoutPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "knockouts.yml");
    }

    public void start() {
        stopAutosave();
        stored.clear();
        dirty = false;
        runtimeEnabled = enabled();

        if (!runtimeEnabled) {
            return;
        }

        loadFromDisk();
        scheduleAutosave();
    }

    public void reload() {
        stopAutosave();
        boolean nextEnabled = enabled();

        if (!nextEnabled) {
            if (runtimeEnabled && dirty) {
                saveToDisk();
            }
            stored.clear();
            dirty = false;
            runtimeEnabled = false;
            return;
        }

        // When persistence is enabled at runtime after being disabled, do not
        // resurrect stale disk sessions. KnockoutManager#onReload will track
        // the currently authoritative runtime sessions immediately afterwards.
        if (!runtimeEnabled) {
            stored.clear();
            dirty = false;
        }
        runtimeEnabled = true;
        scheduleAutosave();
    }

    public void shutdown() {
        stopAutosave();
        if (runtimeEnabled && dirty) {
            saveToDisk();
        }
        runtimeEnabled = false;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("stability.persistence.enabled", true);
    }

    public int storedCount() {
        return stored.size();
    }

    public boolean has(UUID uuid) {
        return enabled() && stored.containsKey(uuid);
    }

    public StoredSession get(UUID uuid) {
        return enabled() ? stored.get(uuid) : null;
    }

    public void track(KnockoutSession session) {
        if (!enabled() || session == null) {
            return;
        }
        stored.put(session.playerId(), StoredSession.from(session));
        dirty = true;
    }

    public void remove(UUID uuid) {
        if (stored.remove(uuid) != null) {
            dirty = true;
        }
    }

    public void flushNow() {
        if (!enabled()) {
            return;
        }
        saveToDisk();
    }

    private void scheduleAutosave() {
        long ticks = Math.max(20L, plugin.getConfig().getLong(
                "stability.persistence.autosave-ticks",
                40L
        ));
        autosaveTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::flushIfDirty,
                ticks,
                ticks
        );
    }

    private void stopAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
    }

    private void flushIfDirty() {
        if (runtimeEnabled && dirty) {
            saveToDisk();
        }
    }

    private void loadFromDisk() {
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("sessions");
        if (root == null) {
            return;
        }

        int loaded = 0;
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }

                String worldName = section.getString("anchor.world", "");
                double x = section.getDouble("anchor.x");
                double y = section.getDouble("anchor.y");
                double z = section.getDouble("anchor.z");
                float yaw = (float) section.getDouble("anchor.yaw");
                float pitch = (float) section.getDouble("anchor.pitch");

                Map<PotionEffectType, PotionEffect> previousEffects = new HashMap<>();
                ConfigurationSection previous = section.getConfigurationSection("previous-effects");
                if (previous != null) {
                    for (String effectName : previous.getKeys(false)) {
                        PotionEffectType type = PotionEffectType.getByName(effectName);
                        ConfigurationSection effectSection = previous.getConfigurationSection(effectName);
                        if (type == null || effectSection == null) {
                            continue;
                        }
                        int duration = Math.max(1, effectSection.getInt("duration", 1));
                        int amplifier = Math.max(0, effectSection.getInt("amplifier", 0));
                        boolean ambient = effectSection.getBoolean("ambient", false);
                        boolean particles = effectSection.getBoolean("particles", true);
                        boolean icon = effectSection.getBoolean("icon", true);
                        previousEffects.put(type, new PotionEffect(
                                type,
                                duration,
                                amplifier,
                                ambient,
                                particles,
                                icon
                        ));
                    }
                }

                Set<PotionEffectType> managedEffects = new HashSet<>();
                for (String effectName : section.getStringList("managed-effects")) {
                    PotionEffectType type = PotionEffectType.getByName(effectName);
                    if (type != null) {
                        managedEffects.add(type);
                    }
                }

                StoredSession record = new StoredSession(
                        uuid,
                        section.getLong("started-at"),
                        section.getLong("expires-at", Long.MAX_VALUE),
                        worldName,
                        x,
                        y,
                        z,
                        yaw,
                        pitch,
                        section.getBoolean("original-swimming", false),
                        section.getBoolean("original-sneaking", false),
                        previousEffects,
                        managedEffects,
                        section.getLong("offline-since", 0L)
                );
                stored.put(uuid, record);
                loaded++;
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping invalid persisted knockout key: " + key);
            }
        }

        if (loaded > 0) {
            plugin.getLogger().info("Loaded " + loaded + " persisted knockout session(s).");
        }
    }

    private void saveToDisk() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 3);

        for (Map.Entry<UUID, StoredSession> entry : stored.entrySet()) {
            String base = "sessions." + entry.getKey();
            StoredSession session = entry.getValue();

            yaml.set(base + ".started-at", session.startedAtMillis());
            yaml.set(base + ".expires-at", session.expiresAtMillis());
            yaml.set(base + ".offline-since", session.offlineSinceMillis());
            yaml.set(base + ".original-swimming", session.originalSwimming());
            yaml.set(base + ".original-sneaking", session.originalSneaking());
            yaml.set(base + ".anchor.world", session.worldName());
            yaml.set(base + ".anchor.x", session.x());
            yaml.set(base + ".anchor.y", session.y());
            yaml.set(base + ".anchor.z", session.z());
            yaml.set(base + ".anchor.yaw", session.yaw());
            yaml.set(base + ".anchor.pitch", session.pitch());

            List<String> managed = new ArrayList<>();
            for (PotionEffectType type : session.managedEffects()) {
                if (type.getName() != null) {
                    managed.add(type.getName());
                }
            }
            yaml.set(base + ".managed-effects", managed);

            for (Map.Entry<PotionEffectType, PotionEffect> effectEntry : session.previousEffects().entrySet()) {
                PotionEffectType type = effectEntry.getKey();
                PotionEffect effect = effectEntry.getValue();
                String effectName = type.getName();
                if (effectName == null) {
                    continue;
                }
                String effectBase = base + ".previous-effects." + effectName;
                yaml.set(effectBase + ".duration", effect.getDuration());
                yaml.set(effectBase + ".amplifier", effect.getAmplifier());
                yaml.set(effectBase + ".ambient", effect.isAmbient());
                yaml.set(effectBase + ".particles", effect.hasParticles());
                yaml.set(effectBase + ".icon", effect.hasIcon());
            }
        }

        if (AtomicYamlStorage.save(yaml, file, plugin.getLogger())) {
            dirty = false;
        }
    }

    public record StoredSession(
            UUID playerId,
            long startedAtMillis,
            long expiresAtMillis,
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            boolean originalSwimming,
            boolean originalSneaking,
            Map<PotionEffectType, PotionEffect> previousEffects,
            Set<PotionEffectType> managedEffects,
            long offlineSinceMillis
    ) {
        public StoredSession {
            previousEffects = Map.copyOf(previousEffects);
            managedEffects = Set.copyOf(managedEffects);
            worldName = worldName == null ? "" : worldName;
        }

        public static StoredSession from(KnockoutSession session) {
            Location anchor = session.anchor();
            World world = anchor.getWorld();
            return new StoredSession(
                    session.playerId(),
                    session.startedAtMillis(),
                    session.expiresAtMillis(),
                    world == null ? "" : world.getName(),
                    anchor.getX(),
                    anchor.getY(),
                    anchor.getZ(),
                    anchor.getYaw(),
                    anchor.getPitch(),
                    session.originalSwimming(),
                    session.originalSneaking(),
                    session.previousEffects(),
                    session.managedEffects(),
                    session.offlineSinceMillis()
            );
        }

        public KnockoutSession toSession(Location anchor) {
            return new KnockoutSession(
                    playerId,
                    startedAtMillis,
                    expiresAtMillis,
                    anchor,
                    originalSwimming,
                    originalSneaking,
                    previousEffects,
                    managedEffects,
                    offlineSinceMillis
            );
        }
    }
}
