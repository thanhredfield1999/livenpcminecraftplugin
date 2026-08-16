package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeManagerShutdownTest {
    @TempDir
    Path dataFolder;

    @Test
    void farmerShutdownContinuesAfterRuntimeSuspendFailure() throws ReflectiveOperationException {
        FarmerManager manager = farmerManager();
        FarmerRuntime failing = mock(FarmerRuntime.class);
        FarmerRuntime remaining = mock(FarmerRuntime.class);
        doThrow(new IllegalStateException("farmer cleanup failed")).when(failing).suspend();
        runtimes(manager).put(new UUID(0L, 0L), failing);
        runtimes(manager).put(new UUID(0L, 1L), remaining);
        UUID leasedNpc = new UUID(0L, 2L);
        assertTrue(manager.navigationLeases().claim(leasedNpc, "before-stop", 1, null));

        assertThrows(IllegalStateException.class, manager::shutdown);

        verify(failing).suspend();
        verify(remaining).suspend();
        assertTrue(runtimes(manager).isEmpty());
        assertTrue(manager.navigationLeases().claim(leasedNpc, "after-stop", 1, null));
    }

    @Test
    void farmerShutdownClearsNavigationLeasesWhenSeatCleanupFails() throws ReflectiveOperationException {
        FarmerManager manager = farmerManager();
        SeatManager seats = mock(SeatManager.class);
        setField(manager, "seatManager", seats);
        doThrow(new IllegalStateException("seat cleanup failed")).when(seats).shutdown(org.mockito.ArgumentMatchers.any());
        UUID leasedNpc = new UUID(0L, 3L);
        assertTrue(manager.navigationLeases().claim(leasedNpc, "before-stop", 1, null));

        assertThrows(IllegalStateException.class, manager::shutdown);

        assertTrue(manager.navigationLeases().claim(leasedNpc, "after-stop", 1, null));
    }

    @Test
    void rancherShutdownContinuesAfterRuntimeSuspendFailure() throws ReflectiveOperationException {
        RancherManager manager = new RancherManager(mock(FarmerManager.class), mock(NpcEconomy.class), mock(VillageStore.class));
        RancherRuntime failing = mock(RancherRuntime.class);
        RancherRuntime remaining = mock(RancherRuntime.class);
        doThrow(new IllegalStateException("rancher cleanup failed")).when(failing).suspend();
        runtimes(manager).put(new UUID(0L, 0L), failing);
        runtimes(manager).put(new UUID(0L, 1L), remaining);

        assertThrows(IllegalStateException.class, manager::shutdown);

        verify(failing).suspend();
        verify(remaining).suspend();
        assertTrue(runtimes(manager).isEmpty());
    }

    @Test
    void civilProfessionShutdownContinuesAfterRuntimeSuspendFailure() throws ReflectiveOperationException {
        CivilProfessionManager manager = new CivilProfessionManager(
                mock(FarmerManager.class), mock(NpcEconomy.class), mock(VillageStore.class),
                mutationPolicy(), mock(ProductionRecipeRegistry.class), mock(MiningRestorationStore.class));
        CivilProfessionRuntime failing = mock(CivilProfessionRuntime.class);
        CivilProfessionRuntime remaining = mock(CivilProfessionRuntime.class);
        doThrow(new IllegalStateException("civil cleanup failed")).when(failing).suspend();
        runtimes(manager).put(new UUID(0L, 0L), failing);
        runtimes(manager).put(new UUID(0L, 1L), remaining);

        assertThrows(IllegalStateException.class, manager::shutdown);

        verify(failing).suspend();
        verify(remaining).suspend();
        assertTrue(runtimes(manager).isEmpty());
    }

    @Test
    void merchantShutdownContinuesAfterRuntimeSuspendFailure() throws ReflectiveOperationException {
        MerchantManager manager = new MerchantManager(mock(FarmerManager.class), mock(VillageStore.class));
        MerchantRuntime failing = mock(MerchantRuntime.class);
        MerchantRuntime remaining = mock(MerchantRuntime.class);
        doThrow(new IllegalStateException("merchant cleanup failed")).when(failing).suspend();
        runtimes(manager).put(new UUID(0L, 0L), failing);
        runtimes(manager).put(new UUID(0L, 1L), remaining);

        assertThrows(IllegalStateException.class, manager::shutdown);

        verify(failing).suspend();
        verify(remaining).suspend();
        assertTrue(runtimes(manager).isEmpty());
    }

    private FarmerManager farmerManager() {
        return new FarmerManager(
                new FarmerStore(dataFolder.toFile(), Logger.getLogger("RuntimeManagerShutdownTest")),
                mock(NpcEconomy.class), mutationPolicy(), mock(VillageStore.class), mock(LivingNpcConfig.class));
    }

    private static WorldMutationPolicy mutationPolicy() {
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isPluginEnabled("WorldGuard")).thenReturn(false);
        return new WorldMutationPolicy(plugins, false);
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<UUID, T> runtimes(Object manager) throws ReflectiveOperationException {
        Field field = manager.getClass().getDeclaredField("runtimes");
        field.setAccessible(true);
        return (Map<UUID, T>) field.get(manager);
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
