package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RancherPathfindingPolicyTest {
    @Test
    void skipsBlockedNearestDeliveryAndUsesReachableFallback() {
        World world = Mockito.mock(World.class);
        Location current = new Location(world, 0, 64, 0);
        Location blocked = new Location(world, 2, 64, 0);
        Location reachable = new Location(world, 6, 64, 0);

        Location selected = RancherRuntime.nearestReachable(
                List.of(reachable, blocked), current, candidate -> candidate != blocked);

        assertEquals(reachable, selected);
    }

    @Test
    void ignoresDeliveryLocationsFromAnotherWorld() {
        World currentWorld = Mockito.mock(World.class);
        World otherWorld = Mockito.mock(World.class);
        Location current = new Location(currentWorld, 0, 64, 0);
        Location other = new Location(otherWorld, 1, 64, 0);

        assertNull(RancherRuntime.nearestReachable(List.of(other), current, candidate -> true));
    }

    @Test
    void returnsNullAfterEverySameWorldDeliveryIsRejected() {
        World world = Mockito.mock(World.class);
        Location current = new Location(world, 0, 64, 0);

        assertNull(RancherRuntime.nearestReachable(
                List.of(new Location(world, 2, 64, 0), new Location(world, 6, 64, 0)),
                current, candidate -> false));
    }
}
