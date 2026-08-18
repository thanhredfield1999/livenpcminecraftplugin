package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.citizensnpcs.api.CitizensAPI;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Regression: Citizens registry có thể ném {@link IllegalStateException} với message
 * "no implementation set" nếu được truy cập trước khi Citizens API sẵn sàng.
 * Các manager phải bắt và skip tick thay vì crash plugin.
 */
class CitizensRegistryGuardTest {

    @Test
    void rancherTickSkipsWhenCitizensRegistryUnavailable() throws ReflectiveOperationException {
        FarmerManager residents = mock(FarmerManager.class);
        RancherManager manager = new RancherManager(residents, mock(NpcEconomy.class), mock(VillageStore.class));
        RancherRuntime runtime = mock(RancherRuntime.class);
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

        // Runtime không bị gọi khi registry unavailable
        org.mockito.Mockito.verifyNoInteractions(runtime);
    }

    @Test
    void needsTickSkipsWhenCitizensRegistryUnavailable() {
        FarmerManager residents = mock(FarmerManager.class);
        NeedsStore store = mock(NeedsStore.class);
        when(store.load()).thenReturn(new java.util.LinkedHashMap<>());
        NeedsManager manager = new NeedsManager(residents, store);
        FarmerDefinition definition = definition(new UUID(0L, 0L));
        when(residents.definitions()).thenReturn(List.of(definition));
        NeedsSettings settings = new NeedsSettings(true, 100L, 200L, 1200L, 1200L);

        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class)) {
            citizens.when(CitizensAPI::getNPCRegistry)
                    .thenThrow(new IllegalStateException("no implementation set"));

            assertDoesNotThrow(() -> manager.tick(100L, settings));
        }

        // Tick hoàn thành mà không crash khi registry unavailable
    }

    private static FarmerDefinition definition(UUID uuid) {
        FarmerDefinition definition = mock(FarmerDefinition.class);
        when(definition.npcUuid()).thenReturn(uuid);
        when(definition.activeRole()).thenReturn(ResidentRole.RANCHER);
        return definition;
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<UUID, T> runtimes(Object manager) throws ReflectiveOperationException {
        Field field = manager.getClass().getDeclaredField("runtimes");
        field.setAccessible(true);
        return (Map<UUID, T>) field.get(manager);
    }
}
