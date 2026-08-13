package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
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
