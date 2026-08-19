package vn.heomc.livingnpc;

import org.bukkit.Location;

final class GateRoute {
    private static final double EXIT_CLEARANCE = 0.3;
    private static final double CROSSING_MARGIN = 0.75;
    private static final int EXIT_CONFIRMATION_TICKS = 2;

    enum Leg {
        STAGING,
        APPROACH,
        EXIT,
        FINAL,
        COMPLETE
    }

    record Candidate(String key, Location staging, Location approach, Location exit) {
        Candidate {
            if (key == null || staging == null || approach == null || exit == null) {
                throw new IllegalArgumentException("Gate candidate phải có key, staging, approach và exit");
            }
            if (staging.getWorld() == null || !staging.getWorld().equals(approach.getWorld())
                    || !staging.getWorld().equals(exit.getWorld())) {
                throw new IllegalArgumentException("Gate candidate phải nằm trong cùng một world");
            }
            if (!finite(staging) || !finite(approach) || !finite(exit)) {
                throw new IllegalArgumentException("Gate candidate phải có tọa độ hữu hạn");
            }
            double deltaX = exit.getX() - approach.getX();
            double deltaZ = exit.getZ() - approach.getZ();
            if (Math.hypot(deltaX, deltaZ) < EXIT_CLEARANCE * 2.0) {
                throw new IllegalArgumentException("Gate candidate không đủ khoảng cách crossing");
            }
        }

        Candidate(String key, Location approach, Location exit) {
            this(key, approach, approach, exit);
        }

        boolean hasDistinctStaging() {
            return Math.abs(staging.getX() - approach.getX()) > 1.0E-9
                    || Math.abs(staging.getY() - approach.getY()) > 1.0E-9
                    || Math.abs(staging.getZ() - approach.getZ()) > 1.0E-9;
        }

        private static boolean finite(Location location) {
            return Double.isFinite(location.getX())
                    && Double.isFinite(location.getY())
                    && Double.isFinite(location.getZ());
        }
    }

    private final Candidate candidate;
    private final Location finalTarget;
    private Leg leg;
    private boolean gateOpenObserved;
    private boolean entrySideObserved;
    private int exitSideConfirmations;
    private boolean exitConfirmationTickObserved;
    private long lastExitConfirmationTick;

    GateRoute(Candidate candidate, Location finalTarget) {
        if (candidate == null || finalTarget == null) {
            throw new IllegalArgumentException("Gate route phải có candidate và final target");
        }
        if (finalTarget.getWorld() == null || !finalTarget.getWorld().equals(candidate.approach().getWorld())
                || !finite(finalTarget)) {
            throw new IllegalArgumentException("Gate route final target phải cùng world và có tọa độ hữu hạn");
        }
        this.candidate = candidate;
        this.finalTarget = finalTarget.clone();
        this.leg = candidate.hasDistinctStaging() ? Leg.STAGING : Leg.APPROACH;
    }

    Leg leg() {
        return leg;
    }

    Location legTarget() {
        return switch (leg) {
            case STAGING -> candidate.staging();
            case APPROACH -> candidate.approach();
            case EXIT -> candidate.exit();
            case FINAL -> finalTarget;
            case COMPLETE -> null;
        };
    }

    static double effectiveMargin(Leg leg, double requestedMargin) {
        return leg == Leg.STAGING || leg == Leg.APPROACH || leg == Leg.EXIT
                ? Math.min(requestedMargin, CROSSING_MARGIN)
                : requestedMargin;
    }

    void observeGateOpened(String gateKey) {
        if (candidate.key().equals(gateKey)) gateOpenObserved = true;
    }

    boolean advanceIfReached(
            Location current, double margin, double verticalTolerance, long serverTick) {
        Location target = legTarget();
        if (target == null) return false;
        double crossingMargin = effectiveMargin(leg, margin);
        if (leg == Leg.STAGING) {
            if (!reached(current, target, crossingMargin, verticalTolerance, false)) return false;
        } else if (leg == Leg.APPROACH) {
            double gateProgress = signedGateProgress(current);
            entrySideObserved |= gateProgress <= -EXIT_CLEARANCE;
            if (!entrySideObserved || gateProgress > -EXIT_CLEARANCE
                    || !reached(current, target, crossingMargin, verticalTolerance, false)) return false;
        } else if (leg == Leg.EXIT) {
            if (signedGateProgress(current) >= EXIT_CLEARANCE
                    && reached(current, target, crossingMargin, verticalTolerance, false)) {
                if (!exitConfirmationTickObserved || serverTick != lastExitConfirmationTick) {
                    exitSideConfirmations++;
                    exitConfirmationTickObserved = true;
                    lastExitConfirmationTick = serverTick;
                }
            } else {
                exitSideConfirmations = 0;
                exitConfirmationTickObserved = false;
            }
            if (exitSideConfirmations < EXIT_CONFIRMATION_TICKS) return false;
        } else if (!reached(current, target, margin, verticalTolerance, true)) {
            return false;
        }
        leg = switch (leg) {
            case STAGING -> Leg.APPROACH;
            case APPROACH -> Leg.EXIT;
            case EXIT -> Leg.FINAL;
            case FINAL -> Leg.COMPLETE;
            case COMPLETE -> Leg.COMPLETE;
        };
        return true;
    }

    boolean gateOpenObserved() {
        return gateOpenObserved;
    }

    boolean approachReached(Location current, double margin, double verticalTolerance) {
        return reached(current, candidate.approach(), margin, verticalTolerance, false);
    }

    void resetToApproach() {
        leg = candidate.hasDistinctStaging() ? Leg.STAGING : Leg.APPROACH;
        exitSideConfirmations = 0;
        exitConfirmationTickObserved = false;
    }

    Candidate candidate() {
        return candidate;
    }

    private double signedGateProgress(Location current) {
        Location approach = candidate.approach();
        Location exit = candidate.exit();
        if (current == null || current.getWorld() == null
                || !current.getWorld().equals(approach.getWorld())
                || !current.getWorld().equals(exit.getWorld())) return Double.NaN;
        double directionX = exit.getX() - approach.getX();
        double directionZ = exit.getZ() - approach.getZ();
        double length = Math.hypot(directionX, directionZ);
        if (length <= 1.0E-9) return Double.NaN;
        double planeX = (approach.getX() + exit.getX()) * 0.5;
        double planeZ = (approach.getZ() + exit.getZ()) * 0.5;
        return (current.getX() - planeX) * directionX / length
                + (current.getZ() - planeZ) * directionZ / length;
    }

    // Gate approach/exit dùng tọa độ đứng chính xác; chỉ FINAL cho phép block-goal fallback.
    // Citizens có thể báo COMPLETED tại block-goal trước khi NPC đạt ô đứng thật, gây kẹt gate.
    private static boolean reached(
            Location current, Location target, double margin, double verticalTolerance, boolean allowBlockGoal) {
        if (current == null || target == null || current.getWorld() == null
                || !current.getWorld().equals(target.getWorld())
                || !finite(current) || !finite(target)
                || !Double.isFinite(margin) || margin < 0.0
                || !Double.isFinite(verticalTolerance) || verticalTolerance <= 0.0) return false;
        if (withinMargin(current, target.getX(), target.getY(), target.getZ(), margin, verticalTolerance)) {
            return true;
        }
        return allowBlockGoal && withinMargin(
                current, target.getBlockX(), target.getBlockY(), target.getBlockZ(), margin, verticalTolerance);
    }

    private static boolean withinMargin(
            Location current, double x, double y, double z, double margin, double verticalTolerance) {
        double deltaX = current.getX() - x;
        double deltaZ = current.getZ() - z;
        return deltaX * deltaX + deltaZ * deltaZ <= margin * margin
                && Math.abs(current.getY() - y) < verticalTolerance;
    }

    private static boolean finite(Location location) {
        return Double.isFinite(location.getX())
                && Double.isFinite(location.getY())
                && Double.isFinite(location.getZ());
    }
}
