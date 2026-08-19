package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.List;

class GateRouteTest {
    @Test
    void gateEventDoesNotAdvanceUntilNpcReachesEachLegTarget() {
        World world = mock(World.class);
        Location approach = new Location(world, 9.5, 64.0, 0.5);
        Location exit = new Location(world, 11.5, 64.0, 0.5);
        Location target = new Location(world, 20.5, 64.0, 0.5);
        GateRoute route = new GateRoute(
                new GateRoute.Candidate("world:10:64:0", approach, exit), target);

        assertEquals(GateRoute.Leg.APPROACH, route.leg());
        assertEquals(approach, route.legTarget());

        route.observeGateOpened("world:10:64:0");
        assertFalse(route.advanceIfReached(new Location(world, 8.5, 64.0, 0.5), 0.3, 1.0, 1L));
        assertEquals(GateRoute.Leg.APPROACH, route.leg());

        assertTrue(route.advanceIfReached(new Location(world, 9.45, 64.0, 0.55), 0.3, 1.0, 2L));
        assertEquals(GateRoute.Leg.EXIT, route.leg());
        assertEquals(exit, route.legTarget());

        assertFalse(route.advanceIfReached(new Location(world, 11.45, 64.0, 0.55), 0.3, 1.0, 3L));
        assertEquals(GateRoute.Leg.EXIT, route.leg());
        assertTrue(route.advanceIfReached(new Location(world, 11.46, 64.0, 0.54), 0.3, 1.0, 4L));
        assertEquals(GateRoute.Leg.FINAL, route.leg());
        assertEquals(target, route.legTarget());

        assertTrue(route.advanceIfReached(new Location(world, 20.45, 64.0, 0.55), 0.3, 1.0, 5L));
        assertEquals(GateRoute.Leg.COMPLETE, route.leg());
    }

    @Test
    void exitLegDoesNotAdvanceBeforeNpcClearsGatePlaneEvenWithLargeNavigationMargin() {
        World world = mock(World.class);
        Location approach = new Location(world, 48.5, 64.0, -17.5);
        Location exit = new Location(world, 49.5, 64.0, -15.5);
        GateRoute route = new GateRoute(
                new GateRoute.Candidate("world:49:64:-17", approach, exit),
                new Location(world, 55.5, 64.0, -10.5));

        assertTrue(route.advanceIfReached(approach, 3.0, 1.0, 1L));
        assertEquals(GateRoute.Leg.EXIT, route.leg());

        assertFalse(route.advanceIfReached(new Location(world, 48.9, 64.0, -16.7), 3.0, 1.0, 2L));
        assertEquals(GateRoute.Leg.EXIT, route.leg());

        assertFalse(route.advanceIfReached(new Location(world, 49.2, 64.0, -16.1), 3.0, 1.0, 3L));
        assertEquals(GateRoute.Leg.EXIT, route.leg());

        assertTrue(route.advanceIfReached(new Location(world, 49.25, 64.0, -16.05), 3.0, 1.0, 4L));
        assertEquals(GateRoute.Leg.FINAL, route.leg());
    }

    @Test
    void gateApproachKhongNhanBlockGoalFallback() {
        World world = mock(World.class);
        GateRoute route = new GateRoute(
                new GateRoute.Candidate("world:10:64:0",
                        new Location(world, 9.5, 64.0, 0.5),
                        new Location(world, 11.5, 64.0, 0.5)),
                new Location(world, 20.5, 64.0, 0.5));

        assertFalse(route.advanceIfReached(new Location(world, 9.0, 64.0, 0.5), 0.3, 1.0, 1L));
        assertEquals(GateRoute.Leg.APPROACH, route.leg());
    }

    @Test
    void exitLegRequiresEntrySideObservationBeforeExitSideConfirmation() {
        World world = mock(World.class);
        Location approach = new Location(world, 48.5, 64.0, -17.5);
        Location exit = new Location(world, 49.5, 64.0, -15.5);
        GateRoute route = new GateRoute(
                new GateRoute.Candidate("world:49:64:-17", approach, exit),
                new Location(world, 55.5, 64.0, -10.5));

        assertFalse(route.advanceIfReached(exit, 3.0, 1.0, 1L));
        assertEquals(GateRoute.Leg.APPROACH, route.leg());
    }

    @Test
    void approachDoesNotAdvanceAfterFarmerDriftsAwayFromEntrySide() {
        World world = mock(World.class);
        Location approach = new Location(world, 48.5, 64.0, -17.5);
        Location exit = new Location(world, 49.5, 64.0, -15.5);
        GateRoute route = new GateRoute(
                new GateRoute.Candidate("world:49:64:-17", approach, exit),
                new Location(world, 55.5, 64.0, -10.5));

        assertFalse(route.advanceIfReached(
                new Location(world, 46.5, 64.0, -21.5), 3.0, 1.0, 1L));
        assertFalse(route.advanceIfReached(
                new Location(world, 49.0, 64.0, -16.5), 3.0, 1.0, 2L));
        assertEquals(GateRoute.Leg.APPROACH, route.leg());
        assertEquals(approach, route.legTarget());
    }

    @Test
    void approachKhongAdvanceSomKhiConCachEntryGeometryMacDuTrongNavigationMargin() {
        World world = mock(World.class);
        Location approach = new Location(world, 62.5, -60.0, -0.5);
        Location exit = new Location(world, 63.5, -60.0, 0.5);
        Location target = new Location(world, 70.5, -60.0, 5.5);
        GateRoute route = new GateRoute(
                new GateRoute.Candidate("world:63:-60:0", approach, exit), target);

        Location current = new Location(world, 62.3, -60.0625, -1.7);
        assertFalse(route.advanceIfReached(current, 1.5, 1.0, 1L));
        assertEquals(GateRoute.Leg.APPROACH, route.leg());
    }

    @Test
    void approachKhongAdvanceKhiCitizensChiDatBlockGoalNhungChuaDenApproachThat() {
        World world = mock(World.class);
        // Tọa độ nguyên văn từ NPC_NAV_END operation=GOING_TO_PLOT_GATE reason=COMPLETED
        // của Farmer Steve lúc 18:10:55 ngày 2026-08-16 trong latest.log.
        Location approach = new Location(world, 60.5, -60.0, -0.5);
        Location exit = new Location(world, 62.5, -60.0, -0.5);
        Location target = new Location(world, 69.5, -60.0, -9.5);
        GateRoute route = new GateRoute(
                new GateRoute.Candidate("StillCliff:61:-60:-1", approach, exit), target);

        // Citizens dừng ở đây với distanceMargin=0.75: cách block-goal (60,-60,-1) là 0.7421,
        // nhưng cách tọa độ tâm block (60.5,-60,-0.5) là 1.2956.
        Location completed = new Location(world, 59.2656, -60.0, -0.8936);
        assertFalse(route.advanceIfReached(completed, 0.75, 1.0, 1L));
        assertEquals(GateRoute.Leg.APPROACH, route.leg());
    }

    @Test
    void candidateQuaNganHoacThoaiHoaBiTuChoiSom() {
        World world = mock(World.class);
        Location approach = new Location(world, 10.0, 64.0, 10.0);

        assertThrows(IllegalArgumentException.class, () -> new GateRoute.Candidate(
                "short", approach, new Location(world, 10.5, 64.0, 10.0)));
        assertThrows(IllegalArgumentException.class, () -> new GateRoute.Candidate(
                "degenerate", approach, approach.clone()));
        assertThrows(IllegalArgumentException.class, () -> new GateRoute.Candidate(
                "nan", approach, new Location(world, Double.NaN, 64.0, 10.0)));
        assertThrows(IllegalArgumentException.class, () -> new GateRoute.Candidate(
                "infinite", approach, new Location(world, Double.POSITIVE_INFINITY, 64.0, 10.0)));
    }

    @Test
    void finalTargetKhacWorldHoacKhongHuuHanBiTuChoiSom() {
        World world = mock(World.class);
        World otherWorld = mock(World.class);
        GateRoute.Candidate candidate = candidate(world, "safe", 10);

        assertThrows(IllegalArgumentException.class, () -> new GateRoute(
                candidate, new Location(otherWorld, 20.5, 64.0, 0.5)));
        assertThrows(IllegalArgumentException.class, () -> new GateRoute(
                candidate, new Location(world, Double.NaN, 64.0, 0.5)));
    }

    @Test
    void failedCandidatesAreTriedOnceThenPlanIsExhausted() {
        World world = mock(World.class);
        Location target = new Location(world, 30.5, 64.0, 0.5);
        GateRoute.Candidate first = candidate(world, "first", 10);
        GateRoute.Candidate second = candidate(world, "second", 20);
        GateRoutePlan plan = new GateRoutePlan(List.of(first, second), target);

        assertEquals("first", plan.next().candidate().key());
        assertEquals("second", plan.failCurrentAndNext().candidate().key());
        assertNull(plan.failCurrentAndNext());
        assertTrue(plan.exhausted());
        assertNull(plan.next());
        assertEquals(2, plan.attemptedCount());
    }

    private static GateRoute.Candidate candidate(World world, String key, int gateX) {
        return new GateRoute.Candidate(
                key,
                new Location(world, gateX - 0.5, 64.0, 0.5),
                new Location(world, gateX + 1.5, 64.0, 0.5));
    }
}
