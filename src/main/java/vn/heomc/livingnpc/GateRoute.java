package vn.heomc.livingnpc;

import org.bukkit.Location;

final class GateRoute {
    private static final double EXIT_CLEARANCE = 0.3;
    private static final double CROSSING_MARGIN = 0.75;
    private static final int EXIT_CONFIRMATION_TICKS = 2;

    enum Leg {
        APPROACH,
        EXIT,
        FINAL,
        COMPLETE
    }

    record Candidate(String key, Location approach, Location exit) {
        Candidate {
            if (key == null || approach == null || exit == null) {
                throw new IllegalArgumentException("Gate candidate phải có key, approach và exit");
            }
            if (approach.getWorld() == null || !approach.getWorld().equals(exit.getWorld())) {
                throw new IllegalArgumentException("Gate candidate phải nằm trong cùng một world");
            }
            if (!finite(approach) || !finite(exit)) {
                throw new IllegalArgumentException("Gate candidate phải có tọa độ hữu hạn");
            }
            double deltaX = exit.getX() - approach.getX();
            double deltaZ = exit.getZ() - approach.getZ();
            if (Math.hypot(deltaX, deltaZ) < EXIT_CLEARANCE * 2.0) {
                throw new IllegalArgumentException("Gate candidate không đủ khoảng cách crossing");
            }
        }

        private static boolean finite(Location location) {
            return Double.isFinite(location.getX())
                    && Double.isFinite(location.getY())
                    && Double.isFinite(location.getZ());
        }
    }

    private final Candidate candidate;
    private final Location finalTarget;
    private Leg leg = Leg.APPROACH;
    private boolean gateOpenObserved;
    private boolean entrySideObserved;
    private int exitSideConfirmations;
    private boolean exitConfirmationTickObserved;
    private long lastExitConfirmationTick;

    GateRoute(Candidate candidate, Location finalTarget) {
        if (candidate == null || finalTarget == null) {
            throw new IllegalArgumentException("Gate route phải có candidate và final target");
        }
        this.candidate = candidate;
        this.finalTarget = finalTarget.clone();
    }

    Leg leg() {
        return leg;
    }

    Location legTarget() {
        return switch (leg) {
            case APPROACH -> candidate.approach();
            case EXIT -> candidate.exit();
            case FINAL -> finalTarget;
            case COMPLETE -> null;
        };
    }

    static double effectiveMargin(Leg leg, double requestedMargin) {
        return leg == Leg.APPROACH || leg == Leg.EXIT
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
        if (leg == Leg.APPROACH) {
            double gateProgress = signedGateProgress(current);
            entrySideObserved |= gateProgress <= -EXIT_CLEARANCE;
            if (!entrySideObserved || gateProgress > -EXIT_CLEARANCE
                    || !reached(current, target, crossingMargin, verticalTolerance)) return false;
        } else if (leg == Leg.EXIT) {
            if (signedGateProgress(current) >= EXIT_CLEARANCE
                    && reached(current, target, crossingMargin, verticalTolerance)) {
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
        } else if (!reached(current, target, margin, verticalTolerance)) {
            return false;
        }
        leg = switch (leg) {
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
        return reached(current, candidate.approach(), Math.max(margin, 2.25), verticalTolerance);
    }

    void resetToApproach() {
        leg = Leg.APPROACH;
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

    // Citizens VectorGoal lượng tử hóa target sang getBlockX/Y/Z, nên một leg có thể được
    // Citizens báo COMPLETED quanh block-goal trong khi vẫn ngoài margin quanh tọa độ tâm block.
    // Chấp nhận cả hai tâm để không từ chối kết quả mà chính Citizens đã xác nhận hoàn tất.
    private static boolean reached(
            Location current, Location target, double margin, double verticalTolerance) {
        if (current == null || target == null || current.getWorld() == null
                || !current.getWorld().equals(target.getWorld())) return false;
        return withinMargin(
                        current, target.getX(), target.getY(), target.getZ(), margin, verticalTolerance)
                || withinMargin(
                        current, target.getBlockX(), target.getBlockY(), target.getBlockZ(),
                        margin, verticalTolerance);
    }

    private static boolean withinMargin(
            Location current, double x, double y, double z, double margin, double verticalTolerance) {
        double deltaX = current.getX() - x;
        double deltaZ = current.getZ() - z;
        return deltaX * deltaX + deltaZ * deltaZ <= margin * margin
                && Math.abs(current.getY() - y) < verticalTolerance;
    }
}
