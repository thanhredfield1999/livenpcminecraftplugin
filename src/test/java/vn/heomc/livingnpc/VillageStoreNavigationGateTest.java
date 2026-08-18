package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VillageStoreNavigationGateTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsValidDistinctNavigationGatesAndPersistsRemoval() throws Exception {
        File file = temporaryDirectory.resolve("villages.yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection village = yaml.createSection("villages.test");
        village.set("name", "Làng thử");
        location(village.createSection("center"), "world", 0, 64, 0);
        location(village.createSection("navigation-gates.0"), "world", 10, 64, 0);
        location(village.createSection("navigation-gates.1"), "world", 10.8, 64.2, 0.7);
        location(village.createSection("navigation-gates.2"), "other", 20, 64, 0);
        location(village.createSection("navigation-gates.3"), "world", 30, 64, 0);
        yaml.save(file);

        VillageStore store = new VillageStore(temporaryDirectory.toFile(), Logger.getAnonymousLogger());

        assertEquals(2, store.get("test").navigationGates().size());
        assertEquals(10, store.get("test").navigationGates().getFirst().location().x());
        assertEquals(30, store.get("test").navigationGates().getLast().location().x());

        store.removeNavigationGate("test", 0);
        VillageStore reloaded = new VillageStore(temporaryDirectory.toFile(), Logger.getAnonymousLogger());

        assertEquals(1, reloaded.get("test").navigationGates().size());
        assertEquals(30, reloaded.get("test").navigationGates().getFirst().location().x());
    }

    private static void location(
            ConfigurationSection section, String world, double x, double y, double z) {
        section.set("world", world);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", 0.0);
        section.set("pitch", 0.0);
    }
}
