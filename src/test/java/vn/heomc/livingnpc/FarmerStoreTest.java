package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FarmerStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void migratesKnownRedfieldResidentAndRoundTripsCharacterData() throws Exception {
        writeLegacyFarmer(ResidentCharacters.THANH_UUID, "ThanhRedfield");
        FarmerStore store = new FarmerStore(tempDir.toFile(), Logger.getAnonymousLogger());

        Map<UUID, FarmerDefinition> loaded = store.load();
        ResidentProfile profile = loaded.get(ResidentCharacters.THANH_UUID).profile();

        assertEquals("Cung", profile.preferredWeapon());
        assertEquals("Keyden_Redfield", profile.relationships().get(ResidentCharacters.KEYDEN_UUID).name());
        assertTrue(store.save(loaded));

        ResidentProfile reloaded = new FarmerStore(tempDir.toFile(), Logger.getAnonymousLogger())
                .load().get(ResidentCharacters.THANH_UUID).profile();
        assertEquals(profile, reloaded);
    }

    @Test
    void oldUnknownResidentDefaultsToEmptyCharacterData() throws Exception {
        UUID uuid = UUID.randomUUID();
        writeLegacyFarmer(uuid, "Unknown");

        ResidentProfile profile = new FarmerStore(tempDir.toFile(), Logger.getAnonymousLogger())
                .load().get(uuid).profile();

        assertEquals("", profile.biography());
        assertTrue(profile.personality().isEmpty());
        assertTrue(profile.goals().isEmpty());
        assertTrue(profile.relationships().isEmpty());
    }

    private void writeLegacyFarmer(UUID uuid, String name) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        String root = "farmers." + uuid;
        yaml.set(root + ".name", name);
        yaml.set(root + ".home.world", "StillCliff");
        yaml.set(root + ".home.x", 1.0);
        yaml.set(root + ".home.y", 64.0);
        yaml.set(root + ".home.z", 2.0);
        yaml.set(root + ".home.yaw", 0.0);
        yaml.set(root + ".home.pitch", 0.0);
        yaml.save(new File(tempDir.toFile(), "farmers.yml"));
    }
}
