package vn.heomc.livingnpc;

import java.util.List;
import org.bukkit.Location;

final class GateRouteCoordinator {
    private static final int MAX_LEG_RESTARTS = 1;

    enum Result {
        IDLE,
        IN_PROGRESS,
        COMPLETE,
        FAILED
    }

    interface Navigation {
        boolean start(Location target, double margin);

        default boolean start(Location target, double margin, GateRoute.Leg leg) {
            return start(target, margin, leg, 0L);
        }

        default boolean start(Location target, double margin, GateRoute.Leg leg, long generation) {
            return start(target, margin);
        }

        /** Candidate sở hữu leg; không suy ngược bằng tọa độ khi hai gate dùng chung waypoint. */
        default boolean start(
                Location target, double margin, GateRoute.Leg leg, long generation, GateRoute.Candidate candidate) {
            return start(target, margin, leg, generation);
        }

        boolean navigating();

        void cancel();

        default boolean recover(Location current, Location target, int radius) {
            return false;
        }

        default void releaseGate(String gateKey) {
        }

        default boolean requestGate(String gateKey) {
            return false;
        }


    }

    private final Navigation navigation;
    private final long timeoutTicks;
    private final double horizontalMargin;
    private final double verticalTolerance;
    private GateRoutePlan plan;
    private GateRoute route;
    private long legStartTick;
    private int legRestartCount;
    private long navigationGeneration;

    long navigationGeneration() {
        return navigationGeneration;
    }

    GateRoute.Leg currentLeg() {
        return route == null ? null : route.leg();
    }


    GateRouteCoordinator(
            Navigation navigation, long timeoutTicks, double horizontalMargin, double verticalTolerance) {
        if (navigation == null || timeoutTicks <= 0L
                || !Double.isFinite(horizontalMargin) || horizontalMargin < 0.0
                || !Double.isFinite(verticalTolerance) || verticalTolerance <= 0.0) {
            throw new IllegalArgumentException("Gate coordinator cần navigation và giới hạn hợp lệ");
        }
        this.navigation = navigation;
        this.timeoutTicks = timeoutTicks;
        this.horizontalMargin = horizontalMargin;
        this.verticalTolerance = verticalTolerance;
    }

    Result start(
            Location current, Location finalTarget, List<GateRoute.Candidate> candidates, long serverTick) {
        if (active()) cancel();
        if (!finite(current) || !finite(finalTarget) || candidates == null || candidates.isEmpty()
                || candidates.stream().anyMatch(java.util.Objects::isNull)
                || !current.getWorld().equals(finalTarget.getWorld())) {
            return Result.FAILED;
        }
        plan = new GateRoutePlan(candidates, finalTarget);
        return startNextCandidate(serverTick);
    }

    Result tick(Location current, long serverTick) {
        if (!active()) return Result.IDLE;
        if (route.advanceIfReached(current, horizontalMargin, verticalTolerance, serverTick)) {
            if (route.leg() == GateRoute.Leg.COMPLETE) {
                navigation.cancel();
                navigation.releaseGate(route.candidate().key());
                route = null;
                if (plan.completeCurrentAndNext() != null) {
                    return startNextCandidate(serverTick);
                }
                clear();
                return Result.COMPLETE;
            }

            if (route.leg() == GateRoute.Leg.EXIT && !route.gateOpenObserved()) {
                if (!navigation.requestGate(route.candidate().key())) {
                    navigation.cancel();
                    route.resetToApproach();
                    legStartTick = serverTick;
                    legRestartCount = 0;
                    return Result.IN_PROGRESS;
                }
                route.observeGateOpened(route.candidate().key());
            }

            if (startLeg(serverTick)) return Result.IN_PROGRESS;
            return rejectCandidateAndContinue(serverTick);
        }
        if (serverTick - legStartTick >= timeoutTicks) {
            navigation.cancel();
            if (legRestartCount >= MAX_LEG_RESTARTS) {
                return rejectCandidateAndContinue(serverTick);
            }
            legRestartCount++;
            return restartLeg(serverTick) ? Result.IN_PROGRESS : rejectCandidateAndContinue(serverTick);
        }
        if (!navigation.navigating()) {
            if (route.leg() == GateRoute.Leg.APPROACH
                    && route.approachReached(current, horizontalMargin, verticalTolerance)) {
                if (advanceApproachAndRequestGate(current, serverTick)) return Result.IN_PROGRESS;
            }
            if (legRestartCount >= MAX_LEG_RESTARTS) {
                return rejectCandidateAndContinue(serverTick);
            }
            legRestartCount++;
            return restartLeg(serverTick) ? Result.IN_PROGRESS : rejectCandidateAndContinue(serverTick);
        }
        return Result.IN_PROGRESS;
    }

    void gateOpenIntent(String gateKey) {
        if (route != null) route.observeGateOpened(gateKey);
    }

    void cancel() {
        if (!active()) return;
        navigation.cancel();
        navigation.releaseGate(route.candidate().key());
        clear();
    }

    boolean active() {
        return plan != null && route != null;
    }

    private Result rejectCandidateAndContinue(long serverTick) {
        if (route != null) navigation.releaseGate(route.candidate().key());
        plan.failCurrentAndNext();
        route = null;
        return startNextCandidate(serverTick);
    }

    private Result startNextCandidate(long serverTick) {
        while (plan != null) {
            GateRoute next = plan.next();
            if (next == null) {
                clear();
                return Result.FAILED;
            }
            route = next;
            if (startLeg(serverTick)) return Result.IN_PROGRESS;
            plan.failCurrentAndNext();
            route = null;
        }
        return Result.FAILED;
    }

    private boolean startLeg(long serverTick) {
        Location target = route.legTarget();
        if (target == null) return false;
        double margin = GateRoute.effectiveMargin(route.leg(), horizontalMargin);
        navigationGeneration++;
        if (!navigation.start(target.clone(), margin, route.leg(), navigationGeneration, route.candidate())) return false;
        legStartTick = serverTick;
        legRestartCount = 0;
        return true;
    }

    private boolean restartLeg(long serverTick) {
        Location target = route.legTarget();
        if (target == null) return false;
        double margin = GateRoute.effectiveMargin(route.leg(), horizontalMargin);
        navigationGeneration++;
        boolean started = navigation.start(target.clone(), margin, route.leg(), navigationGeneration, route.candidate());
        if (started) legStartTick = serverTick;
        return started;
    }

    private boolean advanceApproachAndRequestGate(Location current, long serverTick) {
        if (!route.advanceIfReached(current, horizontalMargin, verticalTolerance, serverTick)) {
            return false;
        }
        if (route.leg() != GateRoute.Leg.EXIT || route.gateOpenObserved()) return false;
        if (!navigation.requestGate(route.candidate().key())) {
            route.resetToApproach();
            legStartTick = serverTick;
            legRestartCount = 0;
            return true;
        }
        route.observeGateOpened(route.candidate().key());
        return startLeg(serverTick);
    }

    private static boolean finite(Location location) {
        return location != null && location.getWorld() != null
                && Double.isFinite(location.getX())
                && Double.isFinite(location.getY())
                && Double.isFinite(location.getZ());
    }

    private void clear() {
        plan = null;
        route = null;
        legStartTick = 0L;
        legRestartCount = 0;
    }
}
