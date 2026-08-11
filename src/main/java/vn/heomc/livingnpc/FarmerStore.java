package vn.heomc.livingnpc;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;

final class FarmerStore {
    private final File file;
    private final Logger logger;
    private boolean writable = true;

    FarmerStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "farmers.yml");
        this.logger = logger;
    }

    Map<UUID, FarmerDefinition> load() {
        Map<UUID, FarmerDefinition> farmers = new LinkedHashMap<>();
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (IOException | InvalidConfigurationException exception) {
                writable = false;
                logger.severe("Could not load farmers.yml; writes are disabled to preserve the file: " + exception.getMessage());
                return farmers;
            }
        }
        ConfigurationSection root = yaml.getConfigurationSection("farmers");
        if (root == null) {
            return farmers;
        }

        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection section = root.getConfigurationSection(key);
                StoredLocation home = StoredLocation.load(section == null ? null : section.getConfigurationSection("home"));
                StoredLocation plot = StoredLocation.load(section == null ? null : section.getConfigurationSection("plot"));
                int radius = section == null ? 0 : section.getInt("plot-radius", 4);
                EnumSet<BehaviorFlag> behaviors = loadBehaviors(section);
                ResidentProfile profile = loadProfile(section);
                String villageId = section == null ? null : section.getString("village-id");
                ResidentRole activeRole = ResidentRole.parse(section == null ? null : section.getString("active-role"));
                Map<ResidentRole, RoleProgress> progress = loadProgress(section, profile);
                Map<ResidentRole, ResidentSchedule> schedules = loadSchedules(section);
                if (home != null) {
                    farmers.put(uuid, new FarmerDefinition(
                            uuid, villageId, home, plot, radius, profile, activeRole, progress, schedules, behaviors));
                }
            } catch (IllegalArgumentException exception) {
                logger.warning("Skipping invalid farmer UUID in farmers.yml: " + key);
            }
        }
        return farmers;
    }

    boolean save(Map<UUID, FarmerDefinition> farmers) {
        if (!writable) {
            logger.severe("Refusing to overwrite farmers.yml after a load failure.");
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("farmers");
        for (FarmerDefinition farmer : farmers.values()) {
            ConfigurationSection section = root.createSection(farmer.npcUuid().toString());
            section.set("village-id", farmer.villageId());
            farmer.home().save(section.createSection("home"));
            if (farmer.plot() != null) {
                farmer.plot().save(section.createSection("plot"));
            }
            section.set("plot-radius", farmer.plotRadius());
            section.set("profile.id", farmer.profile().id());
            section.set("profile.name", farmer.profile().name());
            section.set("profile.gender", farmer.profile().gender());
            section.set("profile.title", farmer.profile().title());
            section.set("profile.roles", farmer.profile().roles().stream().map(ResidentRole::storageKey).sorted().toList());
            section.set("profile.skin", farmer.profile().skin());
            section.set("active-role", farmer.activeRole().storageKey());
            for (ResidentRole role : farmer.profile().roles()) {
                String rolePath = "roles." + role.storageKey();
                section.set(rolePath + ".experience", farmer.progress(role).experience());
                ResidentSchedule schedule = farmer.schedules().get(role);
                if (schedule != null) {
                    section.set(rolePath + ".schedule.start-tick", schedule.startTick());
                    section.set(rolePath + ".schedule.end-tick", schedule.endTick());
                }
            }
            for (BehaviorFlag behavior : BehaviorFlag.values()) {
                section.set("behaviors." + behavior.storageKey(), farmer.enabled(behavior));
            }
        }
        return AtomicYamlStore.save(yaml, file, logger, "farmers.yml");
    }

    private EnumSet<BehaviorFlag> loadBehaviors(ConfigurationSection section) {
        EnumSet<BehaviorFlag> behaviors = BehaviorFlag.safeDefaults();
        if (section == null) {
            return behaviors;
        }
        for (BehaviorFlag behavior : BehaviorFlag.values()) {
            String path = "behaviors." + behavior.storageKey();
            if (section.contains(path)) {
                if (section.getBoolean(path)) {
                    behaviors.add(behavior);
                } else {
                    behaviors.remove(behavior);
                }
            }
        }
        return behaviors;
    }

    private ResidentProfile loadProfile(ConfigurationSection section) {
        if (section == null) {
            return ResidentProfile.custom("Cư dân");
        }
        ConfigurationSection profile = section.getConfigurationSection("profile");
        if (profile == null) {
            return ResidentProfile.custom(section.getString("name", "Cư dân"));
        }
        EnumSet<ResidentRole> roles = EnumSet.noneOf(ResidentRole.class);
        for (String roleName : profile.getStringList("roles")) {
            ResidentRole role = ResidentRole.parse(roleName);
            if (role != null) {
                roles.add(role);
            }
        }
        if (roles.isEmpty()) {
            ResidentRole legacy = ResidentRole.parse(profile.getString("profession", "farmer"));
            roles.add(legacy == null ? ResidentRole.FARMER : legacy);
        }
        return new ResidentProfile(
                profile.getString("id", "custom"),
                profile.getString("name", "Cư dân"),
                profile.getString("gender", "unspecified"),
                profile.getString("title", "Cư dân"),
                roles,
                profile.getString("skin", ""));
    }

    private Map<ResidentRole, RoleProgress> loadProgress(ConfigurationSection section, ResidentProfile profile) {
        Map<ResidentRole, RoleProgress> progress = new java.util.EnumMap<>(ResidentRole.class);
        for (ResidentRole role : profile.roles()) {
            long experience = section == null ? 0L : section.getLong("roles." + role.storageKey() + ".experience", 0L);
            progress.put(role, new RoleProgress(experience));
        }
        return progress;
    }

    private Map<ResidentRole, ResidentSchedule> loadSchedules(ConfigurationSection section) {
        Map<ResidentRole, ResidentSchedule> schedules = new java.util.EnumMap<>(ResidentRole.class);
        if (section == null) {
            return schedules;
        }
        for (ResidentRole role : ResidentRole.values()) {
            String path = "roles." + role.storageKey() + ".schedule";
            if (section.isConfigurationSection(path)) {
                schedules.put(role, new ResidentSchedule(
                        section.getLong(path + ".start-tick"),
                        section.getLong(path + ".end-tick")));
            }
        }
        return schedules;
    }
}
