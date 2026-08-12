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
        if (current == null || !current.center().world().equals(location.getWorld().getName())
                || !isChest(location.getBlock().getType())) {
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
        return deliveryChests(id).stream().findFirst().orElse(null);
    }

    StoredLocation configuredDeliveryChest(String id) {
        VillageDefinition village = get(id);
        return village == null ? null : village.deliveryChest();
    }

    List<Location> deliveryChests(String id) {
        VillageDefinition village = get(id);
        if (village == null) return List.of();
        return village.deliveryLocations().stream()
                .map(StoredLocation::resolve)
                .filter(java.util.Objects::nonNull)
                .filter(location -> location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4))
                .filter(location -> isChest(location.getBlock().getType()))
                .toList();
    }

    boolean removeDeliveryLocation(String id, int index) {
        VillageDefinition current = get(id);
        if (current == null || index < 0 || index >= current.deliveryLocations().size()) return false;
        villages.put(current.id(), current.withoutDeliveryLocation(index));
        if (save()) return true;
        villages.put(current.id(), current);
        return false;
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

    boolean setWorkZone(String id, VillageWorkZoneType type, Location location) {
        VillageDefinition current = get(id);
        if (current == null || type == null
                || !current.center().world().equals(location.getWorld().getName())) {
            return false;
        }
        villages.put(current.id(), current.withWorkZone(type, StoredLocation.from(location)));
        if (save()) return true;
        villages.put(current.id(), current);
        return false;
    }

    VillageDefinition overlappingWorkZone(
            String villageId, VillageWorkZoneType type, Location location, int radius) {
        if (type == null || location == null) return null;
        int combined = Math.max(1, radius) * 2;
        return villages.values().stream()
                .filter(village -> !village.id().equals(normalize(villageId)))
                .filter(village -> {
                    StoredLocation existing = village.workZone(type);
                    return existing != null && existing.world().equals(location.getWorld().getName())
                            && Math.abs(existing.x() - location.getX()) <= combined
                            && Math.abs(existing.z() - location.getZ()) <= combined;
                })
                .findFirst().orElse(null);
    }

    boolean setVisitorGate(String id, Location location) {
        VillageDefinition current = get(id);
        if (current == null || !current.center().world().equals(location.getWorld().getName())) {
            return false;
        }
        villages.put(current.id(), current.withVisitorGate(StoredLocation.from(location)));
        if (save()) return true;
        villages.put(current.id(), current);
        return false;
    }

    boolean setMerchantPoint(String id, java.util.UUID merchantUuid, boolean seller, Location location) {
        VillageDefinition current = get(id);
        if (current == null || merchantUuid == null
                || !current.center().world().equals(location.getWorld().getName())) return false;
        MerchantStall stall = current.merchantStall(merchantUuid);
        if (stall == null) stall = new MerchantStall(merchantUuid, null, null);
        StoredLocation point = StoredLocation.from(location);
        MerchantStall updatedStall = seller ? stall.withSellerPoint(point) : stall.withBuyerPoint(point);
        if (pointUsedByAnotherMerchant(current, updatedStall, point, seller)) return false;
        villages.put(current.id(), current.withMerchantStall(updatedStall));
        if (save()) return true;
        villages.put(current.id(), current);
        return false;
    }

    boolean removeMerchantStall(String id, java.util.UUID merchantUuid) {
        VillageDefinition current = get(id);
        if (current == null) return false;
        VillageDefinition updated = current.withoutMerchantStall(merchantUuid);
        if (updated == current) return false;
        villages.put(current.id(), updated);
        if (save()) return true;
        villages.put(current.id(), current);
        return false;
    }

    boolean setRanchAnimalLimit(String id, int limit) {
        VillageDefinition current = get(id);
        if (current == null) return false;
        villages.put(current.id(), current.withRanchAnimalLimit(limit));
        if (save()) return true;
        villages.put(current.id(), current);
        return false;
    }

    boolean addSeat(String id, SeatDefinition seat) {
        VillageDefinition current = get(id);
        if (current == null || seat == null
                || !current.center().world().equals(seat.location().world())) return false;
        villages.put(current.id(), current.withSeat(seat));
        if (save()) return true;
        villages.put(current.id(), current);
        return false;
    }

    boolean removeSeat(String id, String seatId) {
        VillageDefinition current = get(id);
        if (current == null) return false;
        VillageDefinition updated = current.withoutSeat(seatId);
        if (updated == current) return false;
        villages.put(current.id(), updated);
        if (save()) return true;
        villages.put(current.id(), current);
        return false;
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
                java.util.EnumMap<VillageWorkZoneType, StoredLocation> workZones =
                        new java.util.EnumMap<>(VillageWorkZoneType.class);
                ConfigurationSection workZoneSection = section.getConfigurationSection("work-zones");
                if (workZoneSection != null) {
                    for (String zoneKey : workZoneSection.getKeys(false)) {
                        VillageWorkZoneType type = VillageWorkZoneType.parse(zoneKey);
                        StoredLocation zone = StoredLocation.load(workZoneSection.getConfigurationSection(zoneKey));
                        if (type != null && zone != null) workZones.put(type, zone);
                    }
                }
                java.util.ArrayList<StoredLocation> deliveryLocations = new java.util.ArrayList<>();
                ConfigurationSection deliverySection = section.getConfigurationSection("delivery-locations");
                if (deliverySection != null) {
                    for (String locationKey : deliverySection.getKeys(false)) {
                        StoredLocation location = StoredLocation.load(deliverySection.getConfigurationSection(locationKey));
                        if (location != null) deliveryLocations.add(location);
                    }
                }
                StoredLocation legacyDelivery = StoredLocation.load(section.getConfigurationSection("delivery-chest"));
                if (legacyDelivery != null && deliveryLocations.stream().noneMatch(location -> sameBlock(location, legacyDelivery))) {
                    deliveryLocations.addFirst(legacyDelivery);
                }
                java.util.ArrayList<SeatDefinition> seats = new java.util.ArrayList<>();
                ConfigurationSection seatSection = section.getConfigurationSection("seats");
                if (seatSection != null) {
                    for (String seatId : seatSection.getKeys(false)) {
                        ConfigurationSection storedSeat = seatSection.getConfigurationSection(seatId);
                        StoredLocation location = StoredLocation.load(
                                storedSeat == null ? null : storedSeat.getConfigurationSection("location"));
                        SeatType type = SeatType.parse(storedSeat == null ? null : storedSeat.getString("type"));
                        if (location != null && type != null) seats.add(new SeatDefinition(seatId, location, type));
                    }
                }
                java.util.ArrayList<MerchantStall> merchantStalls = new java.util.ArrayList<>();
                ConfigurationSection stallSection = section.getConfigurationSection("merchant-stalls");
                if (stallSection != null) {
                    for (String merchantKey : stallSection.getKeys(false)) {
                        try {
                            java.util.UUID merchantUuid = java.util.UUID.fromString(merchantKey);
                            ConfigurationSection storedStall = stallSection.getConfigurationSection(merchantKey);
                            if (storedStall != null) merchantStalls.add(new MerchantStall(
                                    merchantUuid,
                                    StoredLocation.load(storedStall.getConfigurationSection("seller-point")),
                                    StoredLocation.load(storedStall.getConfigurationSection("buyer-point"))));
                        } catch (IllegalArgumentException exception) {
                            logger.warning("Bỏ qua UUID Dân buôn không hợp lệ trong villages.yml: " + merchantKey);
                        }
                    }
                }
                villages.put(id, new VillageDefinition(
                        id,
                        section.getString("name", id),
                        center,
                        deliveryLocations,
                        StoredLocation.load(section.getConfigurationSection("market-point")),
                        StoredLocation.load(section.getConfigurationSection("scenic-point")),
                        StoredLocation.load(section.getConfigurationSection("visitor-gate")),
                        section.getInt("ranch-animal-limit", 8),
                        workZones,
                        seats,
                        merchantStalls));
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
            for (int index = 0; index < village.deliveryLocations().size(); index++) {
                village.deliveryLocations().get(index).save(
                        section.createSection("delivery-locations." + index));
            }
            if (village.marketPoint() != null) {
                village.marketPoint().save(section.createSection("market-point"));
            }
            if (village.scenicPoint() != null) {
                village.scenicPoint().save(section.createSection("scenic-point"));
            }
            if (village.visitorGate() != null) {
                village.visitorGate().save(section.createSection("visitor-gate"));
            }
            section.set("ranch-animal-limit", village.ranchAnimalLimit());
            for (Map.Entry<VillageWorkZoneType, StoredLocation> zone : village.workZones().entrySet()) {
                zone.getValue().save(section.createSection("work-zones." + zone.getKey().storageKey()));
            }
            for (SeatDefinition seat : village.seats()) {
                ConfigurationSection seatSection = section.createSection("seats." + seat.id());
                seatSection.set("type", seat.type().name());
                seat.location().save(seatSection.createSection("location"));
            }
            for (MerchantStall stall : village.merchantStalls()) {
                ConfigurationSection stallSection = section.createSection(
                        "merchant-stalls." + stall.merchantUuid());
                if (stall.sellerPoint() != null) {
                    stall.sellerPoint().save(stallSection.createSection("seller-point"));
                }
                if (stall.buyerPoint() != null) {
                    stall.buyerPoint().save(stallSection.createSection("buyer-point"));
                }
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

    private boolean sameBlock(StoredLocation first, StoredLocation second) {
        return first.world().equals(second.world())
                && (int) Math.floor(first.x()) == (int) Math.floor(second.x())
                && (int) Math.floor(first.y()) == (int) Math.floor(second.y())
                && (int) Math.floor(first.z()) == (int) Math.floor(second.z());
    }

    private boolean pointUsedByAnotherMerchant(
            VillageDefinition village, MerchantStall updatedStall, StoredLocation point, boolean seller) {
        for (MerchantStall stall : village.merchantStalls()) {
            if (stall.merchantUuid().equals(updatedStall.merchantUuid())) {
                StoredLocation other = seller ? updatedStall.buyerPoint() : updatedStall.sellerPoint();
                if (other != null && sameBlock(other, point)) return true;
                continue;
            }
            if (stall.sellerPoint() != null && sameBlock(stall.sellerPoint(), point)) return true;
            if (stall.buyerPoint() != null && sameBlock(stall.buyerPoint(), point)) return true;
        }
        return false;
    }
}
