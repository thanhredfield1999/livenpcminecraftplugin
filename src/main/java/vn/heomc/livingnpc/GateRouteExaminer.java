package vn.heomc.livingnpc;

import net.citizensnpcs.api.astar.pathfinder.BlockExaminer;
import net.citizensnpcs.api.astar.pathfinder.BlockSource;
import net.citizensnpcs.api.astar.pathfinder.PathPoint;
import org.bukkit.util.Vector;

/** Giữ Citizens trong corridor leg gate đã chọn; không cho bypass qua đầu hàng rào. */
final class GateRouteExaminer implements BlockExaminer {
    private final GateRoute.Candidate candidate;
    private final GateRoute.Leg leg;

    GateRouteExaminer(GateRoute.Candidate candidate, GateRoute.Leg leg) {
        this.candidate = java.util.Objects.requireNonNull(candidate, "candidate");
        this.leg = java.util.Objects.requireNonNull(leg, "leg");
    }

    @Override
    public float getCost(BlockSource source, PathPoint point) {
        return 0.0F;
    }

    @Override
    public PassableState isPassable(BlockSource source, PathPoint point) {
        Vector position = point.getVector();
        return GateRoute.permitsPathPoint(
                candidate, leg, position.getBlockX(), position.getBlockY(), position.getBlockZ())
                ? PassableState.IGNORE
                : PassableState.IMPASSABLE;
    }
}
