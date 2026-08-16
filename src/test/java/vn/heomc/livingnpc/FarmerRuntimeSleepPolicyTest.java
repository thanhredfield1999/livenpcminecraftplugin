package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.junit.jupiter.api.Test;

class FarmerRuntimeSleepPolicyTest {
    @Test
    void sleepsOnlyDuringConfiguredNightWindow() {
        assertFalse(FarmerRuntime.isBedtime(12999L));
        assertTrue(FarmerRuntime.isBedtime(13000L));
        assertTrue(FarmerRuntime.isBedtime(22999L));
        assertFalse(FarmerRuntime.isBedtime(23000L));
        assertFalse(FarmerRuntime.isBedtime(0L));
    }

    @Test
    void bedtimeHardPreemptsAnActiveNightShift() {
        net.citizensnpcs.api.npc.NPC npc = mock(net.citizensnpcs.api.npc.NPC.class);
        HumanEntity human = mock(HumanEntity.class);
        World world = mock(World.class);
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getEntity()).thenReturn(human);
        when(human.getWorld()).thenReturn(world);
        when(world.getTime()).thenReturn(18000L);
        when(human.isSleeping()).thenReturn(true);

        UUID uuid = UUID.randomUUID();
        ResidentProfile profile = new ResidentProfile(
                "worker", "Worker", "unspecified", "Worker",
                Set.of(ResidentRole.FARMER, ResidentRole.SECURITY), "");
        FarmerDefinition definition = new FarmerDefinition(
                uuid, null, new StoredLocation("world", 0, 64, 0, 0, 0), null, 4,
                profile, ResidentRole.FARMER, Map.of(), Map.of(),
                EnumSet.of(BehaviorFlag.MASTER, BehaviorFlag.FOLLOW_SCHEDULE));
        LivingNpcConfig config = mock(LivingNpcConfig.class);
        when(config.workStartTick()).thenReturn(17000L);
        when(config.workEndTick()).thenReturn(23000L);
        when(config.seasonSix()).thenReturn(new SeasonSixSettings(false, 200L));

        org.bukkit.plugin.PluginManager pluginManager = mock(org.bukkit.plugin.PluginManager.class);
        when(pluginManager.isPluginEnabled("WorldGuard")).thenReturn(false);
        FarmerRuntime runtime = new FarmerRuntime(
                npc, definition, mock(NpcEconomy.class),
                new WorldMutationPolicy(pluginManager, false), mock(VillageStore.class));

        assertTrue(runtime.tickSleep(100L, config),
                "bedtime must preempt an active night shift for a MASTER-enabled NPC");
    }

    @Test
    void selectsNearestSafeCandidateWithoutRunningPathfinding() {
        World world = mock(World.class);
        Location current = new Location(world, 0, 0, 0);
        Location farther = new Location(world, 4, 0, 0);
        Location nearer = new Location(world, 1, 0, 0);

        assertSame(nearer, FarmerRuntime.nearestCandidate(List.of(farther, nearer), current));
    }

    @Test
    void ignoresCandidatesFromAnotherWorld() {
        World currentWorld = mock(World.class);
        World otherWorld = mock(World.class);
        when(currentWorld.getName()).thenReturn("current");
        when(otherWorld.getName()).thenReturn("other");
        Location current = new Location(currentWorld, 0, 0, 0);
        Location other = new Location(otherWorld, 0, 0, 0);

        assertNull(FarmerRuntime.nearestCandidate(List.of(other), current));
    }

    @Test
    void comparesBedOwnershipByWorldAndBlockCoordinates() {
        World world = mock(World.class);
        World otherWorld = mock(World.class);

        assertTrue(FarmerManager.sameBlock(
                new Location(world, -4.0, -57.0, 8.0),
                new Location(world, -3.2, -56.1, 8.9)));
        assertFalse(FarmerManager.sameBlock(
                new Location(world, -4.0, -57.0, 8.0),
                new Location(otherWorld, -4.0, -57.0, 8.0)));
    }
}
