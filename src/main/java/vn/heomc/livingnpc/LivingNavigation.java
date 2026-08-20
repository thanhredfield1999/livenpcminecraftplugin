package vn.heomc.livingnpc;

import java.util.ArrayList;
import java.util.List;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.PathfinderType;
import net.citizensnpcs.api.astar.pathfinder.BlockExaminer;
import net.citizensnpcs.api.astar.pathfinder.DoorExaminer;
import net.citizensnpcs.api.npc.NPC;

final class LivingNavigation {
    private LivingNavigation() {
    }

    static void configureNpc(NPC npc) {
        npc.data().set("pathfinder-open-doors", false);
        allowDoors(npc.getNavigator().getDefaultParameters());
    }

    static NavigatorParameters allowDoors(NavigatorParameters parameters) {
        replaceUnsafeDoorExaminer(parameters);
        if (!parameters.hasExaminer(VillageRouteExaminer.class)) {
            parameters.examiner(new VillageRouteExaminer());
        }
        return parameters.pathfinderType(PathfinderType.CITIZENS)
                .avoidWater(true)
                .fallDistance(1);
    }

    static NavigatorParameters enterBuildings(NavigatorParameters parameters) {
        return allowDoors(parameters);
    }

    static NavigatorParameters constrainGateRoute(
            NavigatorParameters parameters, GateRoute.Candidate candidate, GateRoute.Leg leg) {
        replaceGateRouteExaminer(parameters, candidate, leg);
        return parameters;
    }

    static NavigatorParameters clearGateRouteConstraint(NavigatorParameters parameters) {
        List<BlockExaminer> retained = new ArrayList<>();
        for (BlockExaminer examiner : parameters.examiners()) {
            if (!(examiner instanceof GateRouteExaminer)) retained.add(examiner);
        }
        parameters.clearExaminers();
        for (BlockExaminer examiner : retained) parameters.examiner(examiner);
        return parameters;
    }

    private static void replaceUnsafeDoorExaminer(NavigatorParameters parameters) {
        List<BlockExaminer> retained = new ArrayList<>();
        boolean guardedExaminerPresent = false;
        for (BlockExaminer examiner : parameters.examiners()) {
            if (examiner instanceof DoorExaminer) continue;
            if (examiner instanceof LivingDoorExaminer) {
                if (guardedExaminerPresent) continue;
                guardedExaminerPresent = true;
            }
            retained.add(examiner);
        }
        if (!guardedExaminerPresent) retained.add(new LivingDoorExaminer());
        parameters.clearExaminers();
        for (BlockExaminer examiner : retained) parameters.examiner(examiner);
    }

    private static void replaceGateRouteExaminer(
            NavigatorParameters parameters, GateRoute.Candidate candidate, GateRoute.Leg leg) {
        List<BlockExaminer> retained = new ArrayList<>();
        for (BlockExaminer examiner : parameters.examiners()) {
            if (!(examiner instanceof GateRouteExaminer)) retained.add(examiner);
        }
        retained.add(new GateRouteExaminer(candidate, leg));
        parameters.clearExaminers();
        for (BlockExaminer examiner : retained) parameters.examiner(examiner);
    }
}
