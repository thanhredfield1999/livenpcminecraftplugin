package vn.heomc.livingnpc;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.EnumSet;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class ProfileRegistry {
    private final Map<String, ResidentProfile> profiles = new LinkedHashMap<>();

    ProfileRegistry(File dataFolder) {
        reload(dataFolder);
    }

    void reload(File dataFolder) {
        profiles.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(dataFolder, "profiles.yml"));
        ConfigurationSection root = yaml.getConfigurationSection("profiles");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String name = section.getString("name", id);
            EnumSet<ResidentRole> roles = EnumSet.noneOf(ResidentRole.class);
            for (String roleName : section.getStringList("roles")) {
                ResidentRole role = ResidentRole.parse(roleName);
                if (role != null) {
                    roles.add(role);
                }
            }
            if (roles.isEmpty()) {
                ResidentRole legacy = ResidentRole.parse(section.getString("profession", "farmer"));
                roles.add(legacy == null ? ResidentRole.FARMER : legacy);
            }
            profiles.put(id.toLowerCase(Locale.ROOT), new ResidentProfile(
                    id,
                    name,
                    section.getString("gender", "unspecified"),
                    section.getString("title", "Cư dân"),
                    roles,
                    section.getString("skin", "")));
        }
    }

    ResidentProfile get(String id) {
        return profiles.get(id.toLowerCase(Locale.ROOT));
    }

    ResidentProfile firstUnused(Set<String> usedProfileIds) {
        return profiles.values().stream()
                .filter(profile -> profile.hasRole(ResidentRole.FARMER))
                .filter(profile -> !usedProfileIds.contains(profile.id().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }

    List<String> ids() {
        return List.copyOf(profiles.keySet());
    }
}
