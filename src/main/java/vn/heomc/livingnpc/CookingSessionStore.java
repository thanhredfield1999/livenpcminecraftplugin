package vn.heomc.livingnpc;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class CookingSessionStore {
    private static final int SCHEMA = 1;
    private final File file;
    private final Logger logger;
    private final Map<UUID, CookingSession> sessions = new LinkedHashMap<>();
    private final Map<CookingApplianceKey, UUID> activeLocks = new LinkedHashMap<>();
    private boolean writable = true;

    CookingSessionStore(File dataFolder, Logger logger) {
        file = new File(dataFolder, "cooking-sessions.yml");
        this.logger = logger;
        load();
    }

    boolean writable() {
        return writable;
    }

    boolean locked(CookingApplianceKey appliance) {
        return activeLocks.containsKey(appliance);
    }

    CookingSession activeSession(CookingApplianceKey appliance) {
        UUID sessionId = activeLocks.get(appliance);
        return sessionId == null ? null : sessions.get(sessionId);
    }

    java.util.List<CookingSession> sessions() {
        return java.util.List.copyOf(sessions.values());
    }

    boolean create(CookingSession session) {
        if (!writable || !session.active() || sessions.containsKey(session.sessionId())
                || activeLocks.containsKey(session.appliance())) return false;
        sessions.put(session.sessionId(), session);
        activeLocks.put(session.appliance(), session.sessionId());
        if (save()) return true;
        sessions.remove(session.sessionId());
        activeLocks.remove(session.appliance());
        return false;
    }

    boolean update(CookingSession session) {
        CookingSession previous = sessions.get(session.sessionId());
        if (!writable || previous == null || !previous.appliance().equals(session.appliance())
                || !previous.phase().canTransitionTo(session.phase())) return false;
        sessions.put(session.sessionId(), session);
        if (!session.active()) activeLocks.remove(session.appliance());
        if (save()) return true;
        sessions.put(previous.sessionId(), previous);
        if (previous.active()) activeLocks.put(previous.appliance(), previous.sessionId());
        return false;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            if (yaml.getInt("schema", -1) != SCHEMA) throw new IllegalArgumentException("unsupported schema");
            ConfigurationSection root = yaml.getConfigurationSection("sessions");
            if (root == null) return;
            for (String id : root.getKeys(false)) {
                CookingSession session = loadSession(UUID.fromString(id), root.getConfigurationSection(id));
                sessions.put(session.sessionId(), session);
                if (session.active() && activeLocks.putIfAbsent(session.appliance(), session.sessionId()) != null) {
                    throw new IllegalArgumentException("multiple active sessions for " + session.appliance().storageKey());
                }
            }
        } catch (Exception exception) {
            writable = false;
            logger.severe("Khong the doc cooking-sessions.yml; khoa lo Season 9 da fail-closed: "
                    + exception.getMessage());
        }
    }

    private CookingSession loadSession(UUID id, ConfigurationSection section) {
        if (section == null) throw new IllegalArgumentException("missing session " + id);
        ConfigurationSection block = section.getConfigurationSection("appliance-location");
        CookingApplianceKey key = new CookingApplianceKey(
                required(block, "world"), block.getInt("x"), block.getInt("y"), block.getInt("z"));
        return new CookingSession(
                id, required(section, "village-id"), UUID.fromString(required(section, "cook-uuid")),
                required(section, "appliance-id"), key, required(section, "recipe-id"),
                quantities(section.getConfigurationSection("quantities.reserved")),
                quantities(section.getConfigurationSection("quantities.loaded")),
                quantities(section.getConfigurationSection("quantities.consumed")),
                quantities(section.getConfigurationSection("quantities.residual")),
                quantities(section.getConfigurationSection("quantities.produced")),
                snapshots(section.getConfigurationSection("slot-snapshots")),
                section.getLong("started-active-tick"), section.getLong("elapsed-active-ticks"),
                section.getLong("required-active-ticks"), CookingPhase.valueOf(required(section, "phase")));
    }

    private boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", SCHEMA);
        for (CookingSession session : sessions.values()) {
            ConfigurationSection section = yaml.createSection("sessions." + session.sessionId());
            section.set("village-id", session.villageId());
            section.set("cook-uuid", session.cookUuid().toString());
            section.set("appliance-id", session.applianceId());
            section.set("recipe-id", session.recipeId());
            section.set("phase", session.phase().name());
            section.set("started-active-tick", session.startedActiveTick());
            section.set("elapsed-active-ticks", session.elapsedActiveTicks());
            section.set("required-active-ticks", session.requiredActiveTicks());
            ConfigurationSection block = section.createSection("appliance-location");
            block.set("world", session.appliance().world());
            block.set("x", session.appliance().x());
            block.set("y", session.appliance().y());
            block.set("z", session.appliance().z());
            saveMap(section, "quantities.reserved", session.reserved());
            saveMap(section, "quantities.loaded", session.loaded());
            saveMap(section, "quantities.consumed", session.consumed());
            saveMap(section, "quantities.residual", session.residual());
            saveMap(section, "quantities.produced", session.produced());
            session.slotSnapshots().forEach((slot, value) -> section.set("slot-snapshots." + slot, value));
        }
        return AtomicYamlStore.save(yaml, file, logger, "cooking-sessions.yml");
    }

    private static void saveMap(ConfigurationSection section, String path, Map<String, Integer> values) {
        values.forEach((key, value) -> section.set(path + "." + key, value));
    }

    private static Map<String, Integer> quantities(ConfigurationSection section) {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (section != null) for (String key : section.getKeys(false)) values.put(key, section.getInt(key, -1));
        return values;
    }

    private static Map<Integer, String> snapshots(ConfigurationSection section) {
        Map<Integer, String> values = new LinkedHashMap<>();
        if (section != null) for (String key : section.getKeys(false)) {
            values.put(Integer.parseInt(key), required(section, key));
        }
        return values;
    }

    private static String required(ConfigurationSection section, String path) {
        String value = section == null ? null : section.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + path);
        return value;
    }
}
