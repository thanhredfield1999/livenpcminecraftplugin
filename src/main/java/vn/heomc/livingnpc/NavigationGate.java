package vn.heomc.livingnpc;

import org.bukkit.configuration.ConfigurationSection;

/** Configured navigation gate. Missing access class is legacy and fails closed. */
record NavigationGate(StoredLocation location, String accessClass) {
    NavigationGate {
        if (location == null) throw new IllegalArgumentException("Navigation gate location required");
        accessClass = accessClass == null || accessClass.isBlank()
                ? null : accessClass.trim().toUpperCase(java.util.Locale.ROOT);
    }

    static NavigationGate load(ConfigurationSection section) {
        StoredLocation location = StoredLocation.load(section);
        return location == null ? null : new NavigationGate(location, section.getString("access-class"));
    }

    void save(ConfigurationSection section) {
        location.save(section);
        if (accessClass != null) section.set("access-class", accessClass);
    }

    boolean allows(ResidentRole role) {
        return GateAccessPolicy.mayOpenFenceGate(role, accessClass);
    }
}
