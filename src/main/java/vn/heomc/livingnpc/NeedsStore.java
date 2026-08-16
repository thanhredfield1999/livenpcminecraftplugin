package vn.heomc.livingnpc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class NeedsStore {
    private static final int SCHEMA_VERSION = 1;
    private final File file;
    private final Logger logger;
    private boolean writable = true;

    NeedsStore(File dataFolder, Logger logger) {
        file = new File(dataFolder, "needs.yml");
        this.logger = logger;
    }

    Map<UUID, ResidentNeeds> load() {
        Map<UUID, ResidentNeeds> needs = new LinkedHashMap<>();
        if (!file.exists()) return needs;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            writable = false;
            logger.severe("Could not load needs.yml; writes are disabled to preserve the file: "
                    + exception.getMessage());
            return needs;
        }
        Integer loadedSchemaVersion = schemaVersion(yaml);
        if (loadedSchemaVersion == null) {
            writable = false;
            logger.severe("needs.yml has an invalid schema-version; writes are disabled.");
            return needs;
        }
        if (loadedSchemaVersion > SCHEMA_VERSION) {
            writable = false;
            logger.severe("needs.yml uses unsupported schema version " + loadedSchemaVersion
                    + "; this plugin supports up to " + SCHEMA_VERSION + ". Writes are disabled.");
            return needs;
        }
        ConfigurationSection root = yaml.getConfigurationSection("residents");
        if (root == null) return needs;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            try {
                UUID uuid = UUID.fromString(key);
                needs.put(uuid, new ResidentNeeds(
                        uuid,
                        section.getString("world", ""),
                        section.getInt("hunger", 65),
                        section.getInt("thirst", 55),
                        section.getLong("managed-ticks", 0L),
                        section.getLong("hunger-decay-ticks", 0L),
                        section.getLong("thirst-decay-ticks", 0L)));
            } catch (IllegalArgumentException exception) {
                logger.warning("Skipping invalid resident needs entry: " + key);
            }
        }
        return needs;
    }

    synchronized boolean save(Map<UUID, ResidentNeeds> needs) {
        if (!writable) {
            logger.severe("Refusing to overwrite needs.yml after a load failure.");
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        ConfigurationSection root = yaml.createSection("residents");
        for (ResidentNeeds value : needs.values()) {
            ConfigurationSection section = root.createSection(value.npcUuid().toString());
            section.set("world", value.world());
            section.set("hunger", value.hunger());
            section.set("thirst", value.thirst());
            section.set("managed-ticks", value.managedTicks());
            section.set("hunger-decay-ticks", value.hungerDecayTicks());
            section.set("thirst-decay-ticks", value.thirstDecayTicks());
        }
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            logger.severe("Could not save resident needs: " + exception.getMessage());
            return false;
        }
    }

    private Integer schemaVersion(YamlConfiguration yaml) {
        if (!yaml.contains("schema-version")) return 1;
        Object value = yaml.get("schema-version");
        if (!(value instanceof Number number)) return null;
        double version = number.doubleValue();
        return Double.isFinite(version) && version == Math.rint(version)
                && version > 0 && version <= Integer.MAX_VALUE ? (int) version : null;
    }
}
