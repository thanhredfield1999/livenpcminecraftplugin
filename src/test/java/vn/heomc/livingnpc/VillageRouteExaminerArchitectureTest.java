package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;

import net.citizensnpcs.api.astar.pathfinder.BlockExaminer;
import net.citizensnpcs.api.astar.pathfinder.BlockExaminer.ReplacementNeighbourGenerator;
import org.junit.jupiter.api.Test;

class VillageRouteExaminerArchitectureTest {
    @Test
    void routeSafetyExaminerMustNotReplaceCitizensNeighbourGraph() {
        VillageRouteExaminer examiner = new VillageRouteExaminer();

        assertFalse(ReplacementNeighbourGenerator.class.isAssignableFrom(examiner.getClass()),
                "route safety must not replace Citizens native neighbours; "
                        + "replacement disconnects long stair routes");
        assertTrueBlockExaminer(examiner);
    }

    private static void assertTrueBlockExaminer(Object examiner) {
        assertFalse(!(examiner instanceof BlockExaminer),
                "route safety must remain a Citizens BlockExaminer");
    }
}
