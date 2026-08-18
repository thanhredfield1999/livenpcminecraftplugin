package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

/**
 * R-002: Citizens API readiness — FarmerManager phải fail-closed khi
 * {@code CitizensAPI.getNPCRegistry()} trả về {@code null} trong quá trình
 * khởi tạo (onEnable) hoặc tick.
 */
class CitizensRegistryReadinessTest {
    @TempDir
    Path dataFolder;

    /**
     * Khi Citizens registry chưa khả dụng (null), constructor FarmerManager
     * không được ném exception và không tạo runtime nào.
     */
    @Test
    void constructorDoesNotThrowWhenCitizensRegistryIsNull() throws Exception {
        writeFarmerFixture(dataFolder);

        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(null);

            FarmerManager manager = assertDoesNotThrow(() -> new FarmerManager(
                    new FarmerStore(dataFolder.toFile(), Logger.getLogger("R002")),
                    mock(NpcEconomy.class),
                    mutationPolicy(),
                    mock(VillageStore.class),
                    defaultConfig()));

            assertTrue(runtimes(manager).isEmpty(),
                    "Không runtime nào được tạo khi registry null");
        }
    }

    @Test
    void constructorDoesNotThrowWhenCitizensImplementationIsNotPublished() throws Exception {
        writeFarmerFixture(dataFolder);

        FarmerManager manager = assertDoesNotThrow(() -> new FarmerManager(
                new FarmerStore(dataFolder.toFile(), Logger.getLogger("R002")),
                mock(NpcEconomy.class),
                mutationPolicy(),
                mock(VillageStore.class),
                defaultConfig()));

        assertTrue(runtimes(manager).isEmpty(),
                "Không runtime nào được tạo trước khi Citizens publish implementation");
    }

    /**
     * Khi Citizens registry chưa khả dụng (null), tick không được ném exception.
     */
    @Test
    void tickDoesNotThrowWhenCitizensRegistryIsNull() throws Exception {
        writeFarmerFixture(dataFolder);

        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(null);

            FarmerManager manager = new FarmerManager(
                    new FarmerStore(dataFolder.toFile(), Logger.getLogger("R002")),
                    mock(NpcEconomy.class),
                    mutationPolicy(),
                    mock(VillageStore.class),
                    defaultConfig());

            assertDoesNotThrow(() -> manager.tick(100L));
            assertTrue(runtimes(manager).isEmpty(),
                    "Không runtime nào được tạo khi registry null trong tick");
        }
    }

    /** Tạo một farmers.yml fixture có một NPC definition. */
    private static void writeFarmerFixture(Path dir) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        UUID npcUuid = new UUID(1L, 2L);
        String path = "farmers." + npcUuid;
        yaml.set(path + ".home.world", "world");
        yaml.set(path + ".home.x", 0.0);
        yaml.set(path + ".home.y", 64.0);
        yaml.set(path + ".home.z", 0.0);
        yaml.set(path + ".profile.id", "custom");
        yaml.set(path + ".profile.name", "TestResident");
        yaml.set(path + ".profile.gender", "male");
        yaml.set(path + ".profile.title", "");
        yaml.set(path + ".profile.skin", "");
        yaml.set(path + ".profile.biography", "");
        yaml.set(path + ".profile.personality", "");
        yaml.set(path + ".profile.preferred-weapon", "");
        yaml.set(path + ".profile.goals", "");
        yaml.set(path + ".profile.roles", java.util.List.of("farmer"));
        yaml.set(path + ".active-role", "farmer");
        yaml.set(path + ".plot-radius", 4);
        yaml.save(new File(dir.toFile(), "farmers.yml"));
    }

    private static LivingNpcConfig defaultConfig() {
        LivingNpcConfig config = mock(LivingNpcConfig.class);
        when(config.maxPlotRadius()).thenReturn(8);
        ResidentPatrolSettings patrol = mock(ResidentPatrolSettings.class);
        when(patrol.enabled()).thenReturn(false);
        when(config.residentPatrol()).thenReturn(patrol);
        return config;
    }

    private static WorldMutationPolicy mutationPolicy() {
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.isPluginEnabled("WorldGuard")).thenReturn(false);
        return new WorldMutationPolicy(pluginManager, false);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, FarmerRuntime> runtimes(FarmerManager manager)
            throws ReflectiveOperationException {
        Field field = FarmerManager.class.getDeclaredField("runtimes");
        field.setAccessible(true);
        return (Map<UUID, FarmerRuntime>) field.get(manager);
    }
}
