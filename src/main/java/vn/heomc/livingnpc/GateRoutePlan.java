package vn.heomc.livingnpc;

import java.util.List;
import org.bukkit.Location;

final class GateRoutePlan {
    private final List<GateRoute.Candidate> candidates;
    private final Location finalTarget;
    private int nextCandidateIndex;
    private int attemptedCount;
    private GateRoute current;

    GateRoutePlan(List<GateRoute.Candidate> candidates, Location finalTarget) {
        if (candidates == null || finalTarget == null) {
            throw new IllegalArgumentException("Gate route plan phải có candidates và final target");
        }
        this.candidates = List.copyOf(candidates);
        this.finalTarget = finalTarget.clone();
    }

    GateRoute next() {
        if (current != null) return current;
        if (nextCandidateIndex >= candidates.size()) return null;
        int candidateIndex = nextCandidateIndex++;
        Location routeTarget = candidateIndex + 1 < candidates.size()
                ? candidates.get(candidateIndex + 1).approach()
                : finalTarget;
        current = new GateRoute(candidates.get(candidateIndex), routeTarget);
        attemptedCount++;
        return current;
    }

    GateRoute failCurrentAndNext() {
        current = null;
        return next();
    }

    GateRoute completeCurrentAndNext() {
        current = null;
        return next();
    }

    boolean exhausted() {
        return current == null && nextCandidateIndex >= candidates.size();
    }

    int attemptedCount() {
        return attemptedCount;
    }
}
