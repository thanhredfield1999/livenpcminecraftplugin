package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NeedsStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsNeedsState() {
        UUID uuid = UUID.randomUUID();
        NeedsStore store = new NeedsStore(tempDir.toFile(), Logger.getAnonymousLogger());
        ResidentNeeds value = new ResidentNeeds(uuid, "StillCliff", 62, 48, 900L, 12L, 34L);

        assertTrue(store.save(Map.of(uuid, value)));

        ResidentNeeds loaded = new NeedsStore(tempDir.toFile(), Logger.getAnonymousLogger()).load().get(uuid);
        assertEquals(62, loaded.hunger());
        assertEquals(48, loaded.thirst());
        assertEquals(900L, loaded.managedTicks());
        assertEquals(12L, loaded.hungerDecayTicks());
        assertEquals(34L, loaded.thirstDecayTicks());
        assertEquals("StillCliff", loaded.world());
    }

    @Test
    void malformedFileDisablesWrites() throws Exception {
        File file = tempDir.resolve("needs.yml").toFile();
        Files.writeString(file.toPath(), "residents: [broken");
        NeedsStore store = new NeedsStore(tempDir.toFile(), Logger.getAnonymousLogger());

        assertTrue(store.load().isEmpty());
        assertFalse(store.save(Map.of()));
        assertEquals("residents: [broken", Files.readString(file.toPath()));
    }

    @Test
    void clampsUnsafePersistedValues() throws Exception {
        UUID uuid = UUID.randomUUID();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("residents." + uuid + ".world", "world");
        yaml.set("residents." + uuid + ".hunger", 999);
        yaml.set("residents." + uuid + ".thirst", -20);
        yaml.save(tempDir.resolve("needs.yml").toFile());

        ResidentNeeds loaded = new NeedsStore(tempDir.toFile(), Logger.getAnonymousLogger()).load().get(uuid);
        assertEquals(100, loaded.hunger());
        assertEquals(0, loaded.thirst());
    }
}
