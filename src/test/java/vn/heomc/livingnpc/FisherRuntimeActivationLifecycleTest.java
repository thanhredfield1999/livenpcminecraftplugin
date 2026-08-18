package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.ai.Navigator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class FisherRuntimeActivationLifecycleTest {
    @Test
    void loadedWorkZoneTicksWithoutNearbyPlayer() throws ReflectiveOperationException {
        Fixture fixture = fixture(true);
        setField(fixture.runtime(), "phase", FarmerPhase.RESTING);
        setField(fixture.runtime(), "nextActionTick", 200L);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(fixture.world());

            fixture.runtime().tick(100L, fixture.config());
        }

        assertEquals(FarmerPhase.RESTING, fixture.runtime().phase());
        verify(fixture.world(), never()).getNearbyPlayers(any(Location.class), anyDouble());
    }

    @Test
    void unloadedWorkZoneSuspendsFailClosed() throws ReflectiveOperationException {
        Fixture fixture = fixture(false);
        setField(fixture.runtime(), "phase", FarmerPhase.RESTING);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(fixture.world());

            fixture.runtime().tick(100L, fixture.config());
        }

        assertEquals(FarmerPhase.INACTIVE, fixture.runtime().phase());
        verify(fixture.economy(), never()).canAcceptRoleProduction(
                any(UUID.class), any(String.class), any(ResidentRole.class), anyInt(), anyInt(), anyLong());
    }

    @Test
    void loadedAreaRequiresEveryTouchedChunk() {
        World world = mock(World.class);
        Location center = new Location(world, 15, 64, 15);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.isChunkLoaded(0, 1)).thenReturn(true);
        when(world.isChunkLoaded(1, 0)).thenReturn(true);
        when(world.isChunkLoaded(1, 1)).thenReturn(false);

        assertTrue(!RuntimeChunkAvailability.loadedArea(center, 2));
    }

    @Test
    void loadedRouteFailsClosedWhenAnyWaypointChunkIsMissing() {
        World world = mock(World.class);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.isChunkLoaded(1, 0)).thenReturn(false);
        assertTrue(!RuntimeChunkAvailability.loadedRoute(java.util.List.of(
                new Location(world, 1, 64, 1), new Location(world, 17, 64, 1))));
        when(world.isChunkLoaded(1, 0)).thenReturn(true);
        assertTrue(RuntimeChunkAvailability.loadedRoute(java.util.List.of(
                new Location(world, 1, 64, 1), new Location(world, 17, 64, 1))));
    }

    private Fixture fixture(boolean loaded) {
        UUID uuid = UUID.randomUUID();
        World world = mock(World.class);
        Entity entity = mock(Entity.class);
        NPC npc = mock(NPC.class);
        Navigator navigator = mock(Navigator.class);
        VillageStore villages = mock(VillageStore.class);
        VillageDefinition village = mock(VillageDefinition.class);
        NpcEconomy economy = mock(NpcEconomy.class);
        LivingNpcConfig config = mock(LivingNpcConfig.class);
        ResidentProfile profile = new ResidentProfile(
                "fisher", "Fisher", "unspecified", "Fisher", Set.of(ResidentRole.FISHER), "");
        FarmerDefinition definition = new FarmerDefinition(
                uuid, "village", new StoredLocation("world", 0, 64, 0, 0, 0), null, 4,
                profile, ResidentRole.FISHER, Map.of(), Map.of(), EnumSet.of(BehaviorFlag.MASTER));
        Location location = new Location(world, 0, 64, 0);
        StoredLocation stored = new StoredLocation("world", 0, 64, 0, 0, 0);

        when(npc.getUniqueId()).thenReturn(uuid);
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getEntity()).thenReturn(entity);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.isNavigating()).thenReturn(false);
        when(entity.getWorld()).thenReturn(world);
        when(entity.getLocation()).thenReturn(location);
        when(villages.get("village")).thenReturn(village);
        when(village.id()).thenReturn("village");
        when(village.workZone(VillageWorkZoneType.FISHING)).thenReturn(stored);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(loaded);
        when(world.hasStorm()).thenReturn(false);
        when(world.getTime()).thenReturn(6000L);
        when(world.getFullTime()).thenReturn(6000L);
        when(config.workStartTick()).thenReturn(1000L);
        when(config.workEndTick()).thenReturn(12000L);
        when(config.fisher()).thenReturn(new FisherSettings(20L, 20L, 1.0, 16, 0, 0));
        when(config.navigationRetryBackoffTicks()).thenReturn(60L);
        when(economy.canAcceptRoleProduction(any(), any(), any(), anyInt(), anyInt(), anyLong())).thenReturn(true);

        FisherRuntime runtime = new FisherRuntime(
                npc, definition, economy, villages, new NavigationLeaseManager(), ignored -> { });
        return new Fixture(runtime, config, world, economy);
    }

    private static void setField(FisherRuntime runtime, String name, Object value)
            throws ReflectiveOperationException {
        Field field = FisherRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(runtime, value);
    }

    private record Fixture(FisherRuntime runtime, LivingNpcConfig config, World world, NpcEconomy economy) {
    }
}
