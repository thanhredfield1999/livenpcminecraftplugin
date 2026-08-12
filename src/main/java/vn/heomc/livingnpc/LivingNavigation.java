package vn.heomc.livingnpc;

import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.astar.pathfinder.DoorExaminer;

final class LivingNavigation {
    private LivingNavigation() {
    }

    static NavigatorParameters allowDoors(NavigatorParameters parameters) {
        if (!parameters.hasExaminer(DoorExaminer.class)) {
            parameters.examiner(new DoorExaminer());
        }
        return parameters;
    }
}
