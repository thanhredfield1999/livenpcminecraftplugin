package vn.heomc.livingnpc;

import java.io.File;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class MiningRestorationStore {
    private static final int SCHEMA_VERSION = 1;
    private final File file;
    private final Logger logger;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final ArrayDeque<String> pendingKeys = new ArrayDeque<>();
    private boolean writable = true;

    MiningRestorationStore(File dataFolder, Logger logger) {
        file = new File(dataFolder, "mining-restorations.yml");
        this.logger = logger;
        load();
    }

    boolean record(Block block, BlockData original, Material temporary, long restoreAtMillis) {
        if (!writable || entries.containsKey(key(block))) return false;
        String key = key(block);
        entries.put(key, new Entry(StoredLocation.from(block.getLocation()), original.getAsString(), temporary, restoreAtMillis));
        pendingKeys.addLast(key);
        if (save()) return true;
        entries.remove(key);
        pendingKeys.remove(key);
        logger.severe("MINER_RESTORATION_RECORD_FAILED block=" + key + " result=NO_MUTATION");
        return false;
    }

    void rollback(Block block) {
        String blockKey = key(block);
        Entry entry = entries.remove(blockKey);
        if (entry == null) return;
        pendingKeys.remove(blockKey);
        try {
            block.setBlockData(Bukkit.createBlockData(entry.data()), false);
        } catch (IllegalArgumentException exception) {
            logger.warning("Khong the rollback block mo " + blockKey + ": " + exception.getMessage());
            entries.put(blockKey, entry);
            pendingKeys.addLast(blockKey);
            return;
        }
        if (!save()) {
            entries.put(blockKey, entry);
            pendingKeys.addLast(blockKey);
            logger.severe("MINER_RESTORATION_ROLLBACK_SAVE_FAILED block=" + blockKey + " result=RETRY_PENDING");
        }
    }

    int tick(long nowMillis, int limit) {
        if (limit <= 0 || entries.isEmpty()) return 0;
        int handled = 0;
        int inspected = 0;
        boolean changed = false;
        Map<String, Entry> removed = new LinkedHashMap<>();
        while (inspected < limit && handled < limit && !pendingKeys.isEmpty()) {
            String key = pendingKeys.removeFirst();
            Entry entry = entries.get(key);
            if (entry == null) continue;
            inspected++;
            if (entry.restoreAtMillis() > nowMillis) {
                pendingKeys.addLast(key);
                continue;
            }
            Location location = entry.location().resolve();
            if (location == null || !location.getWorld().isChunkLoaded(
                    location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                pendingKeys.addLast(key);
                continue;
            }
            handled++;
            Block block = location.getBlock();
            if (block.getType() == entry.temporary()) {
                try {
                    block.setBlockData(Bukkit.createBlockData(entry.data()), false);
                } catch (IllegalArgumentException exception) {
                    logger.warning("Khong the phuc hoi block mo " + key + ": " + exception.getMessage());
                    pendingKeys.addLast(key);
                    continue;
                }
            }
            // Player changes are authoritative: never overwrite a block that no longer matches our temporary material.
            entries.remove(key);
            removed.put(key, entry);
            changed = true;
        }
        if (changed && !save()) {
            removed.forEach((key, entry) -> {
                entries.put(key, entry);
                pendingKeys.addLast(key);
            });
            logger.severe("MINER_RESTORATION_SAVE_FAILED entries=" + removed.size() + " result=RETRY_PENDING");
        }
        return inspected;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception exception) {
            writable = false;
            logger.severe("Khong the doc mining-restorations.yml; Miner da fail-closed: " + exception.getMessage());
            return;
        }
        Integer loadedSchemaVersion = schemaVersion(yaml);
        if (loadedSchemaVersion == null || loadedSchemaVersion > SCHEMA_VERSION) {
            writable = false;
            logger.severe("mining-restorations.yml uses an invalid or unsupported schema-version; Miner is fail-closed.");
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("blocks");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            StoredLocation stored = StoredLocation.load(section);
            String data = section == null ? null : section.getString("data");
            Material temporary = section == null ? null : Material.matchMaterial(section.getString("temporary", "COBBLESTONE"));
            if (stored != null && data != null && temporary != null) {
                entries.put(key, new Entry(stored, data, temporary,
                        section.getLong("restore-at", System.currentTimeMillis())));
                pendingKeys.addLast(key);
            }
        }
    }

    private boolean save() {
        if (!writable) return false;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        for (Map.Entry<String, Entry> pending : entries.entrySet()) {
            ConfigurationSection section = yaml.createSection("blocks." + pending.getKey());
            pending.getValue().location().save(section);
            section.set("data", pending.getValue().data());
            section.set("temporary", pending.getValue().temporary().name());
            section.set("restore-at", pending.getValue().restoreAtMillis());
        }
        return AtomicYamlStore.save(yaml, file, logger, "mining-restorations.yml");
    }

    private Integer schemaVersion(YamlConfiguration yaml) {
        if (!yaml.contains("schema-version")) return 1;
        Object value = yaml.get("schema-version");
        if (!(value instanceof Number number)) return null;
        double version = number.doubleValue();
        return Double.isFinite(version) && version == Math.rint(version)
                && version > 0 && version <= Integer.MAX_VALUE ? (int) version : null;
    }

    private String key(Block block) {
        return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    private record Entry(StoredLocation location, String data, Material temporary, long restoreAtMillis) {
    }
}
