package vn.heomc.livingnpc;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class VillageStore {
    private final File file;
    private final Logger logger;
    private final Map<String, VillageDefinition> villages = new LinkedHashMap<>();
    private boolean writable = true;

    VillageStore(File dataFolder, Logger logger) {
        file = new File(dataFolder, "villages.yml");
        this.logger = logger;
        load();
    }

    List<VillageDefinition> villages() {
        return List.copyOf(villages.values());
    }

    VillageDefinition get(String id) {
        return id == null ? null : villages.get(normalize(id));
    }

    boolean create(String id, String name, Location center) {
        String key = normalize(id);
        if (key.isBlank() || villages.containsKey(key)) {
            return false;
        }
        villages.put(key, new VillageDefinition(key, name, StoredLocation.from(center), null, null, null));
        if (save()) {
            return true;
        }
        villages.remove(key);
        return false;
    }

    boolean setDeliveryChest(String id, Location location) {
        VillageDefinition current = get(id);
        if (current == null || !isChest(location.getBlock().getType())) {
            return false;
        }
        villages.put(current.id(), current.withDeliveryChest(StoredLocation.from(location.getBlock().getLocation())));
        if (save()) {
            return true;
        }
        villages.put(current.id(), current);
        return false;
    }

    Location deliveryChest(String id) {
        VillageDefinition village = get(id);
        Location location = village == null || village.deliveryChest() == null ? null : village.deliveryChest().resolve();
        return location != null && isChest(location.getBlock().getType()) ? location : null;
    }

    boolean setSocialPoint(String id, String type, Location location) {
        VillageDefinition current = get(id);
        if (current == null || (!type.equals("cho") && !type.equals("ngamcanh"))
                || !current.center().world().equals(location.getWorld().getName())) {
            return false;
        }
        villages.put(current.id(), current.withSocialPoint(type, StoredLocation.from(location)));
        if (save()) return true;
        villages.put(current.id(), current);
        return false;
    }

    Location socialPoint(String id, String type) {
        VillageDefinition village = get(id);
        StoredLocation point = village == null ? null : type.equals("cho") ? village.marketPoint() : village.scenicPoint();
        return point == null ? null : point.resolve();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            writable = false;
            logger.severe("Không thể đọc villages.yml; đã khóa ghi để bảo vệ dữ liệu: " + exception.getMessage());
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("villages");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            StoredLocation center = StoredLocation.load(section == null ? null : section.getConfigurationSection("center"));
            if (section != null && center != null) {
                String id = normalize(key);
                villages.put(id, new VillageDefinition(
                        id,
                        section.getString("name", id),
                        center,
                        StoredLocation.load(section.getConfigurationSection("delivery-chest")),
                        StoredLocation.load(section.getConfigurationSection("market-point")),
                        StoredLocation.load(section.getConfigurationSection("scenic-point"))));
            }
        }
    }

    private boolean save() {
        if (!writable) {
            logger.severe("Từ chối ghi đè villages.yml sau khi tải file thất bại.");
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("villages");
        for (VillageDefinition village : villages.values()) {
            ConfigurationSection section = root.createSection(village.id());
            section.set("name", village.name());
            village.center().save(section.createSection("center"));
            if (village.deliveryChest() != null) {
                village.deliveryChest().save(section.createSection("delivery-chest"));
            }
            if (village.marketPoint() != null) {
                village.marketPoint().save(section.createSection("market-point"));
            }
            if (village.scenicPoint() != null) {
                village.scenicPoint().save(section.createSection("scenic-point"));
            }
        }
        return AtomicYamlStore.save(yaml, file, logger, "villages.yml");
    }

    private String normalize(String id) {
        return id.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private boolean isChest(Material material) {
        return material == Material.CHEST || material == Material.TRAPPED_CHEST || material == Material.BARREL;
    }
}
