package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class FarmerNavigationPolicyTest {
    @Test
    void acceptsCitizensHorizontalMarginWithVerticalTolerance() {
        World world = mock(World.class);

        assertTrue(FarmerRuntime.navigationTargetReached(
                new Location(world, 1.4, 0.9, 0.0),
                new Location(world, 0.0, 0.0, 0.0),
                1.5));
    }

    @Test
    void acceptsCompletionAroundCitizensBlockGoalForCenteredTarget() {
        World world = mock(World.class);

        assertTrue(FarmerRuntime.navigationTargetReached(
                new Location(world, 27.9181, -60.0625, -9.9026),
                new Location(world, 29.5, -60.0, -8.5),
                1.5));
    }

    @Test
    void rejectsCenteredTargetOutsideCitizensBlockGoalMargin() {
        World world = mock(World.class);

        assertFalse(FarmerRuntime.navigationTargetReached(
                new Location(world, 27.49, -60.0, -9.0),
                new Location(world, 29.5, -60.0, -8.5),
                1.5));
    }

    @Test
    void rejectsTargetsOutsideHorizontalOrVerticalMargin() {
        World world = mock(World.class);
        World otherWorld = mock(World.class);
        Location target = new Location(world, 0.0, 0.0, 0.0);

        assertFalse(FarmerRuntime.navigationTargetReached(
                new Location(world, 1.6, 0.0, 0.0), target, 1.5));
        assertFalse(FarmerRuntime.navigationTargetReached(
                new Location(world, 0.0, 1.0, 0.0), target, 1.5));
        assertFalse(FarmerRuntime.navigationTargetReached(
                new Location(otherWorld, 0.0, 0.0, 0.0), target, 1.5));
    }

    @Test
    void rejectsNegativeOrNonFiniteArrivalMargin() {
        World world = mock(World.class);
        Location current = new Location(world, 0.0, 0.0, 0.0);
        Location target = new Location(world, 0.0, 0.0, 0.0);

        assertFalse(FarmerRuntime.navigationTargetReached(current, target, -1.0));
        assertFalse(FarmerRuntime.navigationTargetReached(current, target, Double.NaN));
        assertFalse(FarmerRuntime.navigationTargetReached(
                current, target, Double.POSITIVE_INFINITY));
    }

    @Test
    void outsidePlotFarmerMustReenterBeforeSelectingCrops() {
        World world = mock(World.class);
        Location plot = new Location(world, 74, -60, -5);

        assertTrue(FarmerRuntime.requiresPlotEntry(
                new Location(world, -7, -57, 5), plot, 6));
        assertFalse(FarmerRuntime.requiresPlotEntry(
                new Location(world, 74, -60, -5), plot, 6));
    }

}
