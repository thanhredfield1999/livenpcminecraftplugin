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
                clear();
                return Result.COMPLETE;
            }
            if (startLeg(serverTick)) return Result.IN_PROGRESS;
            return rejectCandidateAndContinue(serverTick);
        }
        if (serverTick - legStartTick >= timeoutTicks) {
            navigation.cancel();
            return rejectCandidateAndContinue(serverTick);
        }
        if (!navigation.navigating()) {
            if (legRestartCount >= MAX_LEG_RESTARTS) {
                return rejectCandidateAndContinue(serverTick);
            }
            legRestartCount++;
            return restartLeg() ? Result.IN_PROGRESS : rejectCandidateAndContinue(serverTick);
        }
        return Result.IN_PROGRESS;
    }

    void gateOpenIntent(String gateKey) {
        if (route != null) route.observeGateOpened(gateKey);
    }

    void cancel() {
        if (!active()) return;
        navigation.cancel();
        clear();
    }

    boolean active() {
        return plan != null && route != null;
    }

    private Result rejectCandidateAndContinue(long serverTick) {
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

    private boolean restartLeg() {
        Location target = route.legTarget();
        if (target == null) return false;
        double margin = GateRoute.effectiveMargin(route.leg(), horizontalMargin);
        return navigation.start(target.clone(), margin);
    }

    private void clear() {
        plan = null;
        route = null;
        legStartTick = 0L;
        legRestartCount = 0;
    }
}
