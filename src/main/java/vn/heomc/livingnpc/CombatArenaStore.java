package vn.heomc.livingnpc;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class CombatArenaStore {
    private final File file;
    private final Logger logger;
    private boolean writable = true;

    CombatArenaStore(File dataFolder, Logger logger) {
        file = new File(dataFolder, "combat-arenas.yml");
        this.logger = logger;
    }

    Map<String, CombatArena> load() {
        Map<String, CombatArena> arenas = new LinkedHashMap<>();
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (IOException | InvalidConfigurationException exception) {
                writable = false;
                logger.severe("Could not load combat-arenas.yml; writes are disabled: " + exception.getMessage());
                return arenas;
            }
        }
        ConfigurationSection root = yaml.getConfigurationSection("arenas");
        if (root == null) return arenas;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            try {
                if (section != null) {
                    CombatArena arena = new CombatArena(
                            id.toLowerCase(java.util.Locale.ROOT),
                            section.getString("village-id"),
                            UUID.fromString(section.getString("archer-uuid", "")),
                            UUID.fromString(section.getString("swordsman-uuid", "")),
                            StoredLocation.load(section.getConfigurationSection("corner-1")),
                            StoredLocation.load(section.getConfigurationSection("corner-2")),
                            StoredLocation.load(section.getConfigurationSection("retreat")),
                            false,
                            0);
                    boolean duplicate = arenas.values().stream().anyMatch(existing ->
                            existing.archerUuid().equals(arena.archerUuid())
                                    || existing.archerUuid().equals(arena.swordsmanUuid())
                                    || existing.swordsmanUuid().equals(arena.archerUuid())
                                    || existing.swordsmanUuid().equals(arena.swordsmanUuid()));
                    if (duplicate) {
                        logger.warning("Skipping combat arena with duplicate NPC assignment: " + id);
                    } else {
                        arenas.put(arena.id(), arena);
                    }
                }
            } catch (IllegalArgumentException exception) {
                logger.warning("Skipping invalid combat arena: " + id);
            }
        }
        return arenas;
    }

    boolean save(Map<String, CombatArena> arenas) {
        if (!writable) return false;
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("arenas");
        for (CombatArena arena : arenas.values()) {
            ConfigurationSection section = root.createSection(arena.id());
            section.set("village-id", arena.villageId());
            section.set("archer-uuid", arena.archerUuid().toString());
            section.set("swordsman-uuid", arena.swordsmanUuid().toString());
            if (arena.firstCorner() != null) arena.firstCorner().save(section.createSection("corner-1"));
            if (arena.secondCorner() != null) arena.secondCorner().save(section.createSection("corner-2"));
            if (arena.retreatPoint() != null) arena.retreatPoint().save(section.createSection("retreat"));
        }
        return AtomicYamlStore.save(yaml, file, logger, "combat-arenas.yml");
    }
}
