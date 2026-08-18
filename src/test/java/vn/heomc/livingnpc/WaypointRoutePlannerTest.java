package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WaypointRoutePlannerTest {
    @Test
    void rejectsNonFiniteLocations() {
        World world = Mockito.mock(World.class);
        assertTrue(WaypointRoutePlanner.plan(
                new Location(world, Double.NaN, 64, 0),
                new Location(world, 8, 64, 0), 8).isEmpty());
        assertTrue(WaypointRoutePlanner.plan(
                new Location(world, 0, 64, 0),
                new Location(world, Double.POSITIVE_INFINITY, 64, 0), 8).isEmpty());
        assertFalse(new Location(world, Double.NaN, 64, 0).isFinite());
    }

    @Test
    void splitsLongRouteWithinBudget() {
        World world = Mockito.mock(World.class);
        List<Location> route = WaypointRoutePlanner.plan(
                new Location(world, 0, 64, 0), new Location(world, 50, 64, 0), 16);
        assertEquals(4, route.size());
        assertTrue(route.stream().allMatch(location -> location.getWorld() == world));
        assertEquals(50.0, route.get(route.size() - 1).getX(), 0.001);
    }

    @Test
    void keepsShortRouteAsSingleLeg() {
        World world = Mockito.mock(World.class);
        assertEquals(1, WaypointRoutePlanner.plan(
                new Location(world, 0, 64, 0), new Location(world, 6, 64, 0), 16).size());
    }

    @Test
    void rejectsDifferentWorld() {
        World first = Mockito.mock(World.class);
        World second = Mockito.mock(World.class);
        assertTrue(WaypointRoutePlanner.plan(
                new Location(first, 0, 64, 0), new Location(second, 6, 64, 0), 16).isEmpty());
    }

    @Test
    void snapCannotIntroduceMoreThanOneBlockVerticalChange() {
        World world = Mockito.mock(World.class);
        List<Location> route = WaypointRoutePlanner.plan(
                new Location(world, 0, 64, 0), new Location(world, 16, 58, 0), 8);
        Location previous = new Location(world, 0, 64, 0);
        for (Location waypoint : route) {
            assertTrue(Math.abs(waypoint.getY() - previous.getY()) <= 1.000001);
            previous = waypoint;
        }
    }
}
