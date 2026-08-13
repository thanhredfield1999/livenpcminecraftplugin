package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.PathfinderType;
import net.citizensnpcs.api.astar.pathfinder.DoorExaminer;
import org.junit.jupiter.api.Test;

class LivingNavigationTest {
    @Test
    void doorRoutesAlwaysUseCitizensPathfinder() {
        NavigatorParameters parameters = new NavigatorParameters().pathfinderType(PathfinderType.MINECRAFT);

        LivingNavigation.allowDoors(parameters);

        assertEquals(PathfinderType.CITIZENS, parameters.pathfinderType());
        assertTrue(parameters.hasExaminer(DoorExaminer.class));
        assertTrue(parameters.hasExaminer(VillageRouteExaminer.class));
        assertTrue(parameters.avoidWater());
        assertEquals(0, parameters.fallDistance());
    }

    @Test
    void buildingRoutesUseCitizensPathfinderAndOneDoorExaminer() {
        NavigatorParameters parameters = new NavigatorParameters().pathfinderType(PathfinderType.MINECRAFT);

        LivingNavigation.enterBuildings(parameters);
        LivingNavigation.enterBuildings(parameters);

        assertEquals(PathfinderType.CITIZENS, parameters.pathfinderType());
        assertTrue(parameters.hasExaminer(DoorExaminer.class));
        int doorExaminers = 0;
        for (var examiner : parameters.examiners()) {
            if (examiner instanceof DoorExaminer) doorExaminers++;
        }
        assertEquals(1, doorExaminers);
        int routeExaminers = 0;
        for (var examiner : parameters.examiners()) {
            if (examiner instanceof VillageRouteExaminer) routeExaminers++;
        }
        assertEquals(1, routeExaminers);
    }
}
