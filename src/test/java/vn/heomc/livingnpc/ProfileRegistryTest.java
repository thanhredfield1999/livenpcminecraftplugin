package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void readsNewCharacterFieldsAndKeepsLegacyProfilesCompatible() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("profiles.new.name", "New Resident");
        yaml.set("profiles.new.roles", List.of("farmer"));
        yaml.set("profiles.new.biography", "A new story");
        yaml.set("profiles.new.personality", List.of("Calm"));
        yaml.set("profiles.new.preferred-weapon", "Bow");
        yaml.set("profiles.new.goals", List.of("Help the village"));
        yaml.set("profiles.legacy.name", "Legacy Resident");
        yaml.set("profiles.legacy.profession", "farmer");
        yaml.save(new File(tempDir.toFile(), "profiles.yml"));

        ProfileRegistry registry = new ProfileRegistry(tempDir.toFile());

        assertEquals("A new story", registry.get("NEW").biography());
        assertEquals(List.of("Help the village"), registry.get("new").goals());
        assertTrue(registry.get("legacy").hasRole(ResidentRole.FARMER));
        assertTrue(registry.get("legacy").relationships().isEmpty());
    }
}
