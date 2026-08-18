package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class FisherManagerLifecycleTest {
    @Test
    void shutdownContinuesAfterOneRuntimeCleanupFailsAndClearsManagerState()
            throws ReflectiveOperationException {
        FisherManager manager = new FisherManager(
                mock(FarmerManager.class),
                mock(NpcEconomy.class),
                mock(VillageStore.class));
        FisherRuntime failing = mock(FisherRuntime.class);
        FisherRuntime remaining = mock(FisherRuntime.class);
        doThrow(new IllegalStateException("cleanup failed")).when(failing).suspend();
        Map<UUID, FisherRuntime> runtimes = runtimes(manager);
        runtimes.put(new UUID(0L, 0L), failing);
        runtimes.put(new UUID(0L, 1L), remaining);

        assertThrows(IllegalStateException.class, manager::shutdown);

        verify(failing).suspend();
        verify(remaining).suspend();
        assertTrue(runtimes.isEmpty());
    }

    @Test
    void tickContinuesAfterRuntimeAndCleanupBothFail() throws ReflectiveOperationException {
        UUID failingUuid = new UUID(0L, 0L);
        UUID remainingUuid = new UUID(0L, 1L);
        FarmerManager residents = mock(FarmerManager.class);
        FisherManager manager = new FisherManager(
                residents,
                mock(NpcEconomy.class),
                mock(VillageStore.class));
        FarmerDefinition failingDefinition = definition(failingUuid);
        FarmerDefinition remainingDefinition = definition(remainingUuid);
        when(residents.definitions()).thenReturn(List.of(failingDefinition, remainingDefinition));
        FisherRuntime failing = mock(FisherRuntime.class);
        FisherRuntime remaining = mock(FisherRuntime.class);
        LivingNpcConfig config = mock(LivingNpcConfig.class);
        IllegalStateException tickFailure = new IllegalStateException("tick failed");
        IllegalStateException cleanupFailure = new IllegalStateException("cleanup failed");
        doThrow(tickFailure).when(failing).tick(100L, config);
        doThrow(cleanupFailure).when(failing).suspend();
        Map<UUID, FisherRuntime> runtimes = runtimes(manager);
        runtimes.put(failingUuid, failing);
        runtimes.put(remainingUuid, remaining);
        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.getByUniqueId(failingUuid)).thenReturn(mock(NPC.class));
        when(registry.getByUniqueId(remainingUuid)).thenReturn(mock(NPC.class));

        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("FisherManagerLifecycleTest"));

            assertDoesNotThrow(() -> manager.tick(100L, config));
        }

        verify(remaining).tick(100L, config);
        assertEquals(1, tickFailure.getSuppressed().length);
        assertSame(cleanupFailure, tickFailure.getSuppressed()[0]);
    }

    @Test
    void tickContinuesWhenRuntimeAndCleanupThrowTheSameFailureInstance()
            throws ReflectiveOperationException {
        UUID failingUuid = new UUID(0L, 0L);
        UUID remainingUuid = new UUID(0L, 1L);
        FarmerManager residents = mock(FarmerManager.class);
        FisherManager manager = new FisherManager(
                residents,
                mock(NpcEconomy.class),
                mock(VillageStore.class));
        FarmerDefinition failingDefinition = definition(failingUuid);
        FarmerDefinition remainingDefinition = definition(remainingUuid);
        when(residents.definitions()).thenReturn(List.of(failingDefinition, remainingDefinition));
        FisherRuntime failing = mock(FisherRuntime.class);
        FisherRuntime remaining = mock(FisherRuntime.class);
        LivingNpcConfig config = mock(LivingNpcConfig.class);
        IllegalStateException sharedFailure = new IllegalStateException("shared failure");
        doThrow(sharedFailure).when(failing).tick(100L, config);
        doThrow(sharedFailure).when(failing).suspend();
        Map<UUID, FisherRuntime> runtimes = runtimes(manager);
        runtimes.put(failingUuid, failing);
        runtimes.put(remainingUuid, remaining);
        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.getByUniqueId(failingUuid)).thenReturn(mock(NPC.class));
        when(registry.getByUniqueId(remainingUuid)).thenReturn(mock(NPC.class));

        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("FisherManagerLifecycleTest"));

            assertDoesNotThrow(() -> manager.tick(100L, config));
        }

        verify(remaining).tick(100L, config);
        assertEquals(0, sharedFailure.getSuppressed().length);
    }

    @Test
    void staleRuntimeCleanupFailureDoesNotBlockCurrentFishers() throws ReflectiveOperationException {
        UUID staleUuid = new UUID(0L, 0L);
        UUID currentUuid = new UUID(0L, 1L);
        FarmerManager residents = mock(FarmerManager.class);
        FisherManager manager = new FisherManager(
                residents,
                mock(NpcEconomy.class),
                mock(VillageStore.class));
        FarmerDefinition currentDefinition = definition(currentUuid);
        when(residents.definitions()).thenReturn(List.of(currentDefinition));
        FisherRuntime stale = mock(FisherRuntime.class);
        FisherRuntime current = mock(FisherRuntime.class);
        doThrow(new IllegalStateException("stale cleanup failed")).when(stale).suspend();
        Map<UUID, FisherRuntime> runtimes = runtimes(manager);
        runtimes.put(staleUuid, stale);
        runtimes.put(currentUuid, current);
        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.getByUniqueId(currentUuid)).thenReturn(mock(NPC.class));
        LivingNpcConfig config = mock(LivingNpcConfig.class);

        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("FisherManagerLifecycleTest"));

            assertDoesNotThrow(() -> manager.tick(100L, config));
        }

        verify(current).tick(100L, config);
        assertTrue(!runtimes.containsKey(staleUuid));
        assertTrue(runtimes.containsKey(currentUuid));
    }

    @Test
    void sleepingRuntimeCleanupFailureDoesNotBlockCurrentFishers() throws ReflectiveOperationException {
        UUID sleepingUuid = new UUID(0L, 0L);
        UUID currentUuid = new UUID(0L, 1L);
        FarmerManager residents = mock(FarmerManager.class);
        FisherManager manager = new FisherManager(
                residents,
                mock(NpcEconomy.class),
                mock(VillageStore.class));
        FarmerDefinition sleepingDefinition = definition(sleepingUuid);
        FarmerDefinition currentDefinition = definition(currentUuid);
        when(residents.definitions()).thenReturn(List.of(sleepingDefinition, currentDefinition));
        when(residents.sleeping(sleepingUuid)).thenReturn(true);
        FisherRuntime sleeping = mock(FisherRuntime.class);
        FisherRuntime current = mock(FisherRuntime.class);
        doThrow(new IllegalStateException("sleep cleanup failed")).when(sleeping).releaseForSleep();
        Map<UUID, FisherRuntime> runtimes = runtimes(manager);
        runtimes.put(sleepingUuid, sleeping);
        runtimes.put(currentUuid, current);
        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.getByUniqueId(sleepingUuid)).thenReturn(mock(NPC.class));
        when(registry.getByUniqueId(currentUuid)).thenReturn(mock(NPC.class));
        LivingNpcConfig config = mock(LivingNpcConfig.class);

        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("FisherManagerLifecycleTest"));

            assertDoesNotThrow(() -> manager.tick(100L, config));
        }

        verify(current).tick(100L, config);
    }

    @Test
    void definitionUpdateCleanupFailureDoesNotBlockCurrentFishers() throws ReflectiveOperationException {
        UUID failingUuid = new UUID(0L, 0L);
        UUID remainingUuid = new UUID(0L, 1L);
        FarmerManager residents = mock(FarmerManager.class);
        FisherManager manager = new FisherManager(
                residents,
                mock(NpcEconomy.class),
                mock(VillageStore.class));
        FarmerDefinition failingDefinition = definition(failingUuid);
        FarmerDefinition remainingDefinition = definition(remainingUuid);
        when(residents.definitions()).thenReturn(List.of(failingDefinition, remainingDefinition));
        FisherRuntime failing = mock(FisherRuntime.class);
        FisherRuntime remaining = mock(FisherRuntime.class);
        doThrow(new IllegalStateException("definition cleanup failed"))
                .when(failing).updateDefinition(failingDefinition);
        Map<UUID, FisherRuntime> runtimes = runtimes(manager);
        runtimes.put(failingUuid, failing);
        runtimes.put(remainingUuid, remaining);
        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.getByUniqueId(failingUuid)).thenReturn(mock(NPC.class));
        when(registry.getByUniqueId(remainingUuid)).thenReturn(mock(NPC.class));
        LivingNpcConfig config = mock(LivingNpcConfig.class);

        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("FisherManagerLifecycleTest"));

            assertDoesNotThrow(() -> manager.tick(100L, config));
        }

        verify(remaining).updateDefinition(remainingDefinition);
        verify(remaining).tick(100L, config);
    }

    @Test
    void citizensLookupFailureForOneDefinitionDoesNotBlockCurrentFishers()
            throws ReflectiveOperationException {
        UUID failingUuid = new UUID(0L, 0L);
        UUID remainingUuid = new UUID(0L, 1L);
        FarmerManager residents = mock(FarmerManager.class);
        FisherManager manager = new FisherManager(
                residents,
                mock(NpcEconomy.class),
                mock(VillageStore.class));
        FarmerDefinition failingDefinition = definition(failingUuid);
        FarmerDefinition remainingDefinition = definition(remainingUuid);
        when(residents.definitions()).thenReturn(List.of(failingDefinition, remainingDefinition));
        FisherRuntime remaining = mock(FisherRuntime.class);
        runtimes(manager).put(remainingUuid, remaining);
        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.getByUniqueId(failingUuid))
                .thenThrow(new IllegalStateException("lookup failed"));
        when(registry.getByUniqueId(remainingUuid)).thenReturn(mock(NPC.class));
        LivingNpcConfig config = mock(LivingNpcConfig.class);

        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("FisherManagerLifecycleTest"));

            assertDoesNotThrow(() -> manager.tick(100L, config));
        }

        verify(remaining).tick(100L, config);
    }

    @Test
    void runtimeCreationFailureForOneDefinitionDoesNotBlockCurrentFishers()
            throws ReflectiveOperationException {
        UUID failingUuid = new UUID(0L, 0L);
        UUID remainingUuid = new UUID(0L, 1L);
        FarmerManager residents = mock(FarmerManager.class);
        FisherManager manager = new FisherManager(
                residents,
                mock(NpcEconomy.class),
                mock(VillageStore.class));
        FarmerDefinition failingDefinition = definition(failingUuid);
        FarmerDefinition remainingDefinition = definition(remainingUuid);
        when(residents.definitions()).thenReturn(List.of(failingDefinition, remainingDefinition));
        when(residents.navigationLeases())
                .thenThrow(new IllegalStateException("runtime creation failed"));
        FisherRuntime remaining = mock(FisherRuntime.class);
        runtimes(manager).put(remainingUuid, remaining);
        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.getByUniqueId(failingUuid)).thenReturn(mock(NPC.class));
        when(registry.getByUniqueId(remainingUuid)).thenReturn(mock(NPC.class));
        LivingNpcConfig config = mock(LivingNpcConfig.class);

        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("FisherManagerLifecycleTest"));

            assertDoesNotThrow(() -> manager.tick(100L, config));
        }

        verify(remaining).tick(100L, config);
    }

    @Test
    void perDefinitionDecisionFailuresDoNotBlockCurrentFishers()
            throws ReflectiveOperationException {
        UUID roleFailureUuid = new UUID(0L, 0L);
        UUID sleepFailureUuid = new UUID(0L, 1L);
        UUID activeRoleFailureUuid = new UUID(0L, 2L);
        UUID remainingUuid = new UUID(0L, 3L);
        FarmerManager residents = mock(FarmerManager.class);
        FisherManager manager = new FisherManager(
                residents,
                mock(NpcEconomy.class),
                mock(VillageStore.class));
        FarmerDefinition roleFailureDefinition = definition(roleFailureUuid);
        FarmerDefinition sleepFailureDefinition = definition(sleepFailureUuid);
        FarmerDefinition activeRoleFailureDefinition = definition(activeRoleFailureUuid);
        FarmerDefinition remainingDefinition = definition(remainingUuid);
        when(residents.definitions()).thenReturn(List.of(
                roleFailureDefinition, sleepFailureDefinition,
                activeRoleFailureDefinition, remainingDefinition));
        when(residents.roleChangedThisTick(roleFailureUuid))
                .thenThrow(new IllegalStateException("role state failed"));
        when(residents.sleeping(sleepFailureUuid))
                .thenThrow(new IllegalStateException("sleep state failed"));
        when(activeRoleFailureDefinition.activeRole())
                .thenThrow(new IllegalStateException("active role failed"));
        FisherRuntime roleFailure = mock(FisherRuntime.class);
        FisherRuntime sleepFailure = mock(FisherRuntime.class);
        FisherRuntime activeRoleFailure = mock(FisherRuntime.class);
        FisherRuntime remaining = mock(FisherRuntime.class);
        Map<UUID, FisherRuntime> runtimes = runtimes(manager);
        runtimes.put(roleFailureUuid, roleFailure);
        runtimes.put(sleepFailureUuid, sleepFailure);
        runtimes.put(activeRoleFailureUuid, activeRoleFailure);
        runtimes.put(remainingUuid, remaining);
        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.getByUniqueId(roleFailureUuid)).thenReturn(mock(NPC.class));
        when(registry.getByUniqueId(sleepFailureUuid)).thenReturn(mock(NPC.class));
        when(registry.getByUniqueId(activeRoleFailureUuid)).thenReturn(mock(NPC.class));
        when(registry.getByUniqueId(remainingUuid)).thenReturn(mock(NPC.class));
        LivingNpcConfig config = mock(LivingNpcConfig.class);

        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("FisherManagerLifecycleTest"));

            assertDoesNotThrow(() -> manager.tick(100L, config));
        }

        verify(residents).sleeping(sleepFailureUuid);
        verify(remaining).tick(100L, config);
    }

    @Test
    void tickSkipsWhenCitizensRegistryUnavailable() throws ReflectiveOperationException {
        FarmerManager residents = mock(FarmerManager.class);
        FisherManager manager = new FisherManager(
                residents,
                mock(NpcEconomy.class),
                mock(VillageStore.class));
        FisherRuntime runtime = mock(FisherRuntime.class);
        UUID npcUuid = new UUID(0L, 0L);
        runtimes(manager).put(npcUuid, runtime);
        FarmerDefinition definition = definition(npcUuid);
        when(residents.definitions()).thenReturn(List.of(definition));
        LivingNpcConfig config = mock(LivingNpcConfig.class);

        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class)) {
            citizens.when(CitizensAPI::getNPCRegistry)
                    .thenThrow(new IllegalStateException("no implementation set"));

            assertDoesNotThrow(() -> manager.tick(100L, config));
        }

        // Runtime không bị gọi tick, suspend hoặc bất kỳ method nào — tick trả về ngay
        org.mockito.Mockito.verifyNoInteractions(runtime);
    }

    private static FarmerDefinition definition(UUID uuid) {
        FarmerDefinition definition = mock(FarmerDefinition.class);
        when(definition.npcUuid()).thenReturn(uuid);
        when(definition.activeRole()).thenReturn(ResidentRole.FISHER);
        return definition;
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, FisherRuntime> runtimes(FisherManager manager)
            throws ReflectiveOperationException {
        Field field = FisherManager.class.getDeclaredField("runtimes");
        field.setAccessible(true);
        return (Map<UUID, FisherRuntime>) field.get(manager);
    }
}
