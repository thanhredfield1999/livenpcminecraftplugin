package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WaypointRouteCoordinatorTest {
    @Test
    void rejectsInvalidCoordinatorConfiguration() {
        World world = Mockito.mock(World.class);
        List<Location> waypoints = List.of(new Location(world, 8, 64, 0));
        FakeNavigation navigation = new FakeNavigation();

        assertThrows(IllegalArgumentException.class,
                () -> new WaypointRouteCoordinator(null, waypoints, 0.75, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> new WaypointRouteCoordinator(navigation, waypoints, -0.1, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> new WaypointRouteCoordinator(navigation, waypoints, Double.NaN, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> new WaypointRouteCoordinator(navigation, waypoints, 0.75, 0L));
    }

    @Test
    void deadlineNearLongMaxDoesNotTimeoutEarly() {
        FakeNavigation navigation = new FakeNavigation();
        World world = Mockito.mock(World.class);
        WaypointRouteCoordinator coordinator = new WaypointRouteCoordinator(
                navigation,
                List.of(new Location(world, 8, 64, 0)),
                0.75,
                10L);
        long startTick = Long.MAX_VALUE - 5L;

        assertEquals(WaypointRouteCoordinator.Result.IN_PROGRESS, coordinator.start(startTick));
        assertEquals(WaypointRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(new Location(world, 0, 64, 0), startTick + 1L));
        assertEquals(0, navigation.cancelCount);
        assertEquals(WaypointRouteCoordinator.Result.IN_PROGRESS,
                coordinator.tick(new Location(world, 0, 64, 0), startTick + 9L));
        assertEquals(0, navigation.cancelCount);
        assertEquals(WaypointRouteCoordinator.Result.FAILED,
                coordinator.tick(new Location(world, 0, 64, 0), startTick + 10L));
        assertEquals(1, navigation.cancelCount);
    }

    @Test
    void failedStartCancelsAnyStaleNavigationBeforeFailure() {
        FakeNavigation navigation = new FakeNavigation();
        navigation.navigating = true;
        navigation.failStart = true;
        World world = Mockito.mock(World.class);
        WaypointRouteCoordinator coordinator = new WaypointRouteCoordinator(
                navigation,
                List.of(new Location(world, 8, 64, 0)),
                0.75,
                10L);

        assertEquals(WaypointRouteCoordinator.Result.FAILED, coordinator.start(100L));
        assertEquals(1, navigation.cancelCount);
    }

    @Test
    void timeoutCancelsActiveWaypointNavigationBeforeFailure() {
        FakeNavigation navigation = new FakeNavigation();
        World world = Mockito.mock(World.class);
        WaypointRouteCoordinator coordinator = new WaypointRouteCoordinator(
                navigation,
                List.of(new Location(world, 8, 64, 0)),
                0.75,
                10L);

        assertEquals(WaypointRouteCoordinator.Result.IN_PROGRESS, coordinator.start(100L));
        assertEquals(WaypointRouteCoordinator.Result.FAILED,
                coordinator.tick(new Location(world, 0, 64, 0), 110L));
        assertEquals(1, navigation.cancelCount);
    }

    private static final class FakeNavigation implements WaypointRouteCoordinator.Navigation {
        private final List<Location> targets = new ArrayList<>();
        private int cancelCount;
        private boolean navigating;
        private boolean failStart;

        @Override
        public boolean start(Location target) {
            targets.add(target);
            if (failStart) {
                failStart = false;
                return false;
            }
            navigating = true;
            return true;
        }

        @Override
        public boolean navigating() {
            return navigating;
        }

        @Override
        public void cancel() {
            cancelCount++;
            navigating = false;
        }
    }
}

