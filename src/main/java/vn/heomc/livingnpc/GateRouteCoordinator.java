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

        boolean navigating();

        void cancel();

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
        if (current == null || finalTarget == null || candidates == null || candidates.isEmpty()
                || current.getWorld() == null || !current.getWorld().equals(finalTarget.getWorld())) {
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
            return rejectCandidateAndContinue(serverTick);
        }
        if (!navigation.navigating()) {
            if (route.leg() == GateRoute.Leg.APPROACH
                    && route.approachReached(current, horizontalMargin, verticalTolerance)) {
                if (advanceApproachAndRequestGate(current, serverTick)) return Result.IN_PROGRESS;
            }
            if (legRestartCount >= MAX_LEG_RESTARTS) {
                navigation.releaseGate(route.candidate().key());
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
        if (!navigation.start(target.clone(), margin)) return false;
        legStartTick = serverTick;
        legRestartCount = 0;
        return true;
    }

    private boolean restartLeg(long serverTick) {
        Location target = route.legTarget();
        if (target == null) return false;
        double margin = GateRoute.effectiveMargin(route.leg(), horizontalMargin);
        boolean started = navigation.start(target.clone(), margin);
        if (started) legStartTick = serverTick;
        return started;
    }

    private boolean advanceApproachAndRequestGate(Location current, long serverTick) {
        if (!route.advanceIfReached(current, Math.max(horizontalMargin, 2.25), verticalTolerance, serverTick)) {
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

    private void clear() {
        plan = null;
        route = null;
        legStartTick = 0L;
        legRestartCount = 0;
    }
}
