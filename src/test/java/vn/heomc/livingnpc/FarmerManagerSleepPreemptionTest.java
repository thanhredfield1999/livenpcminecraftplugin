package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class FarmerManagerSleepPreemptionTest {
    @TempDir
    Path dataFolder;

    @Test
    void tickSleepRunsBeforeRoleWorkEvenWhenExternallyBusy()
            throws ReflectiveOperationException {
        UUID npcUuid = new UUID(0L, 0L);
        LivingNpcConfig config = mock(LivingNpcConfig.class);
        ResidentPatrolSettings patrol = mock(ResidentPatrolSettings.class);
        when(patrol.enabled()).thenReturn(false);
        when(config.residentPatrol()).thenReturn(patrol);
        FarmerManager manager = new FarmerManager(
                new FarmerStore(dataFolder.toFile(), Logger.getLogger("FarmerManagerSleepPreemptionTest")),
                mock(NpcEconomy.class),
                mutationPolicy(),
                mock(VillageStore.class),
                config);
        FarmerDefinition definition = mock(FarmerDefinition.class);
        when(definition.npcUuid()).thenReturn(npcUuid);
        when(definition.activeRole()).thenReturn(ResidentRole.FARMER);
        FarmerRuntime runtime = mock(FarmerRuntime.class);
        when(runtime.npcUuid()).thenReturn(npcUuid);
        when(runtime.tickSleep(100L, config)).thenReturn(true);
        definitions(manager).put(npcUuid, definition);
        runtimes(manager).put(npcUuid, runtime);

        manager.setExternallyBusy(Set.of(npcUuid));

        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.getByUniqueId(npcUuid)).thenReturn(mock(net.citizensnpcs.api.npc.NPC.class));
        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
            assertDoesNotThrow(() -> manager.tick(100L));
        }

        verify(runtime).tickSleep(100L, config);
        verify(runtime, never()).tick(100L, config);
    }

    @Test
    void roleWorkIsSkippedWhileTheResidentIsSleeping()
            throws ReflectiveOperationException {
        UUID npcUuid = new UUID(0L, 0L);
        LivingNpcConfig config = mock(LivingNpcConfig.class);
        ResidentPatrolSettings patrol = mock(ResidentPatrolSettings.class);
        when(patrol.enabled()).thenReturn(false);
        when(config.residentPatrol()).thenReturn(patrol);
        FarmerManager manager = new FarmerManager(
                new FarmerStore(dataFolder.toFile(), Logger.getLogger("FarmerManagerSleepPreemptionTest")),
                mock(NpcEconomy.class),
                mutationPolicy(),
                mock(VillageStore.class),
                config);
        FarmerDefinition definition = mock(FarmerDefinition.class);
        when(definition.npcUuid()).thenReturn(npcUuid);
        when(definition.activeRole()).thenReturn(ResidentRole.FARMER);
        FarmerRuntime runtime = mock(FarmerRuntime.class);
        when(runtime.npcUuid()).thenReturn(npcUuid);
        when(runtime.tickSleep(100L, config)).thenReturn(true);
        definitions(manager).put(npcUuid, definition);
        runtimes(manager).put(npcUuid, runtime);

        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.getByUniqueId(npcUuid)).thenReturn(mock(net.citizensnpcs.api.npc.NPC.class));
        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
            assertDoesNotThrow(() -> manager.tick(100L));
        }

        verify(runtime).tickSleep(100L, config);
        verify(runtime, never()).tick(100L, config);
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

    @SuppressWarnings("unchecked")
    private static Map<UUID, FarmerDefinition> definitions(FarmerManager manager)
            throws ReflectiveOperationException {
        Field field = FarmerManager.class.getDeclaredField("definitions");
        field.setAccessible(true);
        return (Map<UUID, FarmerDefinition>) field.get(manager);
    }
}
