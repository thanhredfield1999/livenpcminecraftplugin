package vn.heomc.livingnpc;

import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.PathfinderType;
import net.citizensnpcs.api.astar.pathfinder.DoorExaminer;

final class LivingNavigation {
    private LivingNavigation() {
    }

    static NavigatorParameters allowDoors(NavigatorParameters parameters) {
        if (!parameters.hasExaminer(DoorExaminer.class)) {
            parameters.examiner(new DoorExaminer());
        }
        if (!parameters.hasExaminer(VillageRouteExaminer.class)) {
            parameters.examiner(new VillageRouteExaminer());
        }
        return parameters.pathfinderType(PathfinderType.CITIZENS)
                .avoidWater(true)
                .fallDistance(0);
    }

    static NavigatorParameters enterBuildings(NavigatorParameters parameters) {
        return allowDoors(parameters);
    }
}
