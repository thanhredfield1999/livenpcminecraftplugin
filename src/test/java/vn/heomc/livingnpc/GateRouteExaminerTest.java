package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.citizensnpcs.api.astar.pathfinder.BlockExaminer;
import net.citizensnpcs.api.astar.pathfinder.BlockSource;
import net.citizensnpcs.api.astar.pathfinder.PathPoint;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class GateRouteExaminerTest {
    @Test
    void exitLegRejectsBypassNodeOutsideConfiguredGateAxis() {
        World world = mock(World.class);
        GateRoute.Candidate gate = new GateRoute.Candidate(
                "world:5:64:0",
                new Location(world, 3.5, 64.0, 0.5),
                new Location(world, 4.5, 64.0, 0.5),
                new Location(world, 6.5, 64.0, 0.5));
        GateRouteExaminer examiner = new GateRouteExaminer(gate, GateRoute.Leg.EXIT);
        PathPoint axis = pointAt(5, 64, 0);
        PathPoint bypass = pointAt(5, 64, 1);

        assertEquals(BlockExaminer.PassableState.IGNORE, examiner.isPassable(mock(BlockSource.class), axis));
        assertEquals(BlockExaminer.PassableState.IMPASSABLE, examiner.isPassable(mock(BlockSource.class), bypass));
    }

    private static PathPoint pointAt(int x, int y, int z) {
        PathPoint point = mock(PathPoint.class);
        when(point.getVector()).thenReturn(new Vector(x, y, z));
        return point;
    }
}
