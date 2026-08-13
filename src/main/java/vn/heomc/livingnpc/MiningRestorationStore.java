package vn.heomc.livingnpc;

import java.io.File;
import java.util.Iterator;
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
    private final File file;
    private final Logger logger;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
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
        if (save()) return true;
        entries.remove(key);
        return false;
    }

    void rollback(Block block) {
        Entry entry = entries.remove(key(block));
        if (entry == null) return;
        try {
            block.setBlockData(Bukkit.createBlockData(entry.data()), false);
        } catch (IllegalArgumentException exception) {
            logger.warning("Khong the rollback block mo " + key(block) + ": " + exception.getMessage());
            entries.put(key(block), entry);
        }
        save();
    }

    void tick(long nowMillis, int limit) {
        int handled = 0;
        boolean changed = false;
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext() && handled < limit) {
            Map.Entry<String, Entry> pending = iterator.next();
            Entry entry = pending.getValue();
            if (entry.restoreAtMillis() > nowMillis) continue;
            Location location = entry.location().resolve();
            if (location == null || !location.getWorld().isChunkLoaded(
                    location.getBlockX() >> 4, location.getBlockZ() >> 4)) continue;
            handled++;
            Block block = location.getBlock();
            if (block.getType() == entry.temporary()) {
                try {
                    block.setBlockData(Bukkit.createBlockData(entry.data()), false);
                } catch (IllegalArgumentException exception) {
                    logger.warning("Khong the phuc hoi block mo " + pending.getKey() + ": " + exception.getMessage());
                    continue;
                }
            }
            // Player changes are authoritative: never overwrite a block that no longer matches our temporary material.
            iterator.remove();
            changed = true;
        }
        if (changed) save();
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
        ConfigurationSection root = yaml.getConfigurationSection("blocks");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            StoredLocation stored = StoredLocation.load(section);
            String data = section == null ? null : section.getString("data");
            Material temporary = section == null ? null : Material.matchMaterial(section.getString("temporary", "COBBLESTONE"));
            if (stored != null && data != null && temporary != null) entries.put(key, new Entry(
                    stored, data, temporary, section.getLong("restore-at", System.currentTimeMillis())));
        }
    }

    private boolean save() {
        if (!writable) return false;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Entry> pending : entries.entrySet()) {
            ConfigurationSection section = yaml.createSection("blocks." + pending.getKey());
            pending.getValue().location().save(section);
            section.set("data", pending.getValue().data());
            section.set("temporary", pending.getValue().temporary().name());
            section.set("restore-at", pending.getValue().restoreAtMillis());
        }
        return AtomicYamlStore.save(yaml, file, logger, "mining-restorations.yml");
    }

    private String key(Block block) {
        return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    private record Entry(StoredLocation location, String data, Material temporary, long restoreAtMillis) {
    }
}
