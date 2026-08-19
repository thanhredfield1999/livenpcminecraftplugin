package vn.heomc.livingnpc;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;

/** Chia tuyến dài thành các chặng ngắn để Citizens không cạn ngân sách A*. */
final class WaypointRoutePlanner {
    static final double DEFAULT_SEGMENT_BLOCKS = 8.0;
    private WaypointRoutePlanner() {
    }

    static List<Location> plan(Location start, Location target, double segmentBlocks) {
        if (start == null || target == null || !start.isFinite() || !target.isFinite()
                || start.getWorld() == null || !start.getWorld().equals(target.getWorld())
                || !Double.isFinite(segmentBlocks) || segmentBlocks <= 0.0) {
            return List.of();
        }
        double dx = target.getX() - start.getX();
        double dy = target.getY() - start.getY();
        double dz = target.getZ() - start.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int distanceLegs = (int) Math.ceil(distance / segmentBlocks);
        int verticalLegs = (int) Math.ceil(Math.abs(dy));
        int legs = Math.max(1, Math.max(distanceLegs, verticalLegs));
        List<Location> result = new ArrayList<>(legs);
        double previousY = start.getY();
        for (int index = 1; index <= legs; index++) {
            double fraction = (double) index / legs;
            Location interpolated = new Location(start.getWorld(),
                    start.getX() + dx * fraction,
                    start.getY() + dy * fraction,
                    start.getZ() + dz * fraction,
                    target.getYaw(), target.getPitch());
            Location waypoint = index == legs
                    ? target.clone() : snapToStanding(interpolated, previousY);
            result.add(waypoint);
            previousY = waypoint.getY();
        }
        return List.copyOf(result);
    }

    private static Location snapToStanding(Location location, double previousY) {
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    Location candidate = location.clone().add(dx, dy, dz);
                    var feet = candidate.getBlock();
                    if (feet == null) return location;
                    if (Math.abs(candidate.getY() - previousY) > 1.0 + 1.0e-6) continue;
                    if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()
                            || feet.getRelative(0, -1, 0).isPassable()) continue;
                    double distance = candidate.distanceSquared(location);
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best == null ? location : best;
    }
}

final class WaypointRouteCoordinator {
    enum Result { IN_PROGRESS, COMPLETE, FAILED }

    interface Navigation {
        boolean start(Location target);
        boolean navigating();
        void cancel();

        default boolean recover(Location current, Location target, int radius) {
            return false;
        }
    }

    private final Navigation navigation;
    private final List<Location> waypoints;
    private final double margin;
    private int index;
    private int recoveryAttempts;
    private long startedTick;
    private final long timeoutTicks;

    WaypointRouteCoordinator(Navigation navigation, List<Location> waypoints,
            double margin, long timeoutTicks) {
        if (navigation == null || waypoints == null || !Double.isFinite(margin) || margin < 0.0
                || timeoutTicks <= 0L
                || waypoints.stream().anyMatch(location -> location == null || !location.isFinite()
                        || location.getWorld() == null)) {
            throw new IllegalArgumentException("Waypoint coordinator cần cấu hình hợp lệ");
        }
        this.navigation = navigation;
        this.waypoints = List.copyOf(waypoints);
        this.margin = margin;
        this.timeoutTicks = timeoutTicks;
    }

    Result start(long tick) {
        if (waypoints.isEmpty()) return Result.FAILED;
        if (!startCurrent(tick)) {
            navigation.cancel();
            return Result.FAILED;
        }
        return Result.IN_PROGRESS;
    }

    Result tick(Location current, long tick) {
        if (index >= waypoints.size()) return Result.COMPLETE;
        if (FarmerRuntime.navigationTargetReached(current, waypoints.get(index), margin)) {
            navigation.cancel();
            index++;
            recoveryAttempts = 0;
            if (index >= waypoints.size()) return Result.COMPLETE;
            if (!startCurrent(tick)) {
                navigation.cancel();
                return Result.FAILED;
            }
            return Result.IN_PROGRESS;
        }
        if (tick - startedTick >= timeoutTicks || !navigation.navigating()) {
            navigation.cancel();
            if (recoveryAttempts < 1
                    && navigation.recover(current, waypoints.get(index), 2)) {
                recoveryAttempts++;
                if (startCurrent(tick)) return Result.IN_PROGRESS;
            }
            return Result.FAILED;
        }
        return Result.IN_PROGRESS;
    }

    void cancel() {
        navigation.cancel();
    }

    private boolean startCurrent(long tick) {
        startedTick = tick;
        return navigation.start(waypoints.get(index).clone());
    }
}
    