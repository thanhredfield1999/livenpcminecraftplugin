package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.PathfinderType;
import net.citizensnpcs.api.astar.pathfinder.DoorExaminer;
import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class LivingNavigationTest {
    @Test
    void doorRoutesAlwaysUseCitizensPathfinder() {
        NavigatorParameters parameters = new NavigatorParameters().pathfinderType(PathfinderType.MINECRAFT);

        LivingNavigation.allowDoors(parameters);

        assertEquals(PathfinderType.CITIZENS, parameters.pathfinderType());
        assertTrue(parameters.hasExaminer(LivingDoorExaminer.class));
        assertFalse(parameters.hasExaminer(DoorExaminer.class));
        assertTrue(parameters.hasExaminer(VillageRouteExaminer.class));
        assertTrue(parameters.avoidWater());
        assertEquals(1, parameters.fallDistance());
    }

    /**
     * Regression: fallDistance=0 caused Citizens A* to reject every downward neighbor,
     * making long routes (55–126 blocks) through terrain with any Y change impossible.
     * path=absent after 4 ticks on GOING_HOME/GOING_TO_BED operations.
     * fallDistance=1 allows normal 1-block steps (no fall damage); VillageRouteExaminer
     * still removes unsupported cliff edges independently.
     * See: docs/incidents/2026-08-16-gate-route-block-goal-completion-deadlock.md
     */
    @Test
    void allowsOneBlockFallToEnableLongRoutesThroughUnevenTerrain() {
        NavigatorParameters parameters = new NavigatorParameters();

        LivingNavigation.allowDoors(parameters);

        assertEquals(1, parameters.fallDistance(),
                "fallDistance must be 1 to allow normal 1-block steps; "
                + "0 blocks Citizens A* from finding paths through terrain with any elevation change");
    }

    @Test
    void buildingRoutesReplaceCitizensDoorExaminerWithoutDuplicatingTheSafeExaminer() {
        NavigatorParameters parameters = new NavigatorParameters().pathfinderType(PathfinderType.MINECRAFT);
        parameters.examiner(new DoorExaminer());

        LivingNavigation.enterBuildings(parameters);
        LivingNavigation.enterBuildings(parameters);

        assertEquals(PathfinderType.CITIZENS, parameters.pathfinderType());
        assertFalse(parameters.hasExaminer(DoorExaminer.class));
        int doorExaminers = 0;
        for (var examiner : parameters.examiners()) {
            if (examiner instanceof LivingDoorExaminer) doorExaminers++;
        }
        assertEquals(1, doorExaminers);
        int routeExaminers = 0;
        for (var examiner : parameters.examiners()) {
            if (examiner instanceof VillageRouteExaminer) routeExaminers++;
        }
        assertEquals(1, routeExaminers);
    }

    @Test
    void everyLivingNpcNavigationDisablesCitizensAutomaticDoorExaminer() {
        NPC npc = mock(NPC.class);
        MetadataStore data = mock(MetadataStore.class);
        Navigator navigator = mock(Navigator.class);
        NavigatorParameters parameters = new NavigatorParameters().examiner(new DoorExaminer());
        when(npc.data()).thenReturn(data);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.getDefaultParameters()).thenReturn(parameters);

        LivingNavigation.configureNpc(npc);

        verify(data).set("pathfinder-open-doors", false);
        assertFalse(parameters.hasExaminer(DoorExaminer.class));
        assertTrue(parameters.hasExaminer(LivingDoorExaminer.class));
    }

    @Test
    void gateRouteConstraintReplacesOldConstraintAndCanBeCleared() {
        World world = mock(World.class);
        GateRoute.Candidate gate = new GateRoute.Candidate(
                "world:5:64:0",
                new Location(world, 3.5, 64.0, 0.5),
                new Location(world, 4.5, 64.0, 0.5),
                new Location(world, 6.5, 64.0, 0.5));
        NavigatorParameters parameters = new NavigatorParameters();

        LivingNavigation.allowDoors(parameters);
        LivingNavigation.constrainGateRoute(parameters, gate, GateRoute.Leg.APPROACH);
        LivingNavigation.constrainGateRoute(parameters, gate, GateRoute.Leg.EXIT);

        assertEquals(1, countGateRouteExaminers(parameters));
        LivingNavigation.clearGateRouteConstraint(parameters);
        assertEquals(0, countGateRouteExaminers(parameters));
        assertTrue(parameters.hasExaminer(VillageRouteExaminer.class));
        assertTrue(parameters.hasExaminer(LivingDoorExaminer.class));
    }

    private static int countGateRouteExaminers(NavigatorParameters parameters) {
        int count = 0;
        for (var examiner : parameters.examiners()) {
            if (examiner instanceof GateRouteExaminer) count++;
        }
        return count;
    }

}
