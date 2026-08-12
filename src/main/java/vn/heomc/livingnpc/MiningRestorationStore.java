package vn.heomc.livingnpc;

import java.io.File;
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

    MiningRestorationStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "mining-restorations.yml");
        this.logger = logger;
        recoverPending();
    }

    boolean record(Block block, BlockData data) {
        String key = key(block);
        entries.put(key, new Entry(StoredLocation.from(block.getLocation()), data.getAsString()));
        if (save()) return true;
        entries.remove(key);
        return false;
    }

    void completed(Block block) {
        if (entries.remove(key(block)) != null) save();
    }

    private void recoverPending() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("blocks");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            StoredLocation stored = StoredLocation.load(section);
            String data = section == null ? null : section.getString("data");
            if (stored == null || data == null) continue;
            Location location = stored.resolve();
            if (location == null) {
                entries.put(key, new Entry(stored, data));
                continue;
            }
            Block block = location.getBlock();
            if (block.getType() == Material.AIR) {
                try {
                    block.setBlockData(Bukkit.createBlockData(data), true);
                } catch (IllegalArgumentException exception) {
                    logger.warning("Could not restore mining block " + key + ": " + exception.getMessage());
                    entries.put(key, new Entry(stored, data));
                }
            }
        }
        save();
    }

    private boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Entry> pending : entries.entrySet()) {
            ConfigurationSection section = yaml.createSection("blocks." + pending.getKey());
            pending.getValue().location().save(section);
            section.set("data", pending.getValue().data());
        }
        return AtomicYamlStore.save(yaml, file, logger, "mining-restorations.yml");
    }

    private String key(Block block) {
        return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    private record Entry(StoredLocation location, String data) {
    }
}
