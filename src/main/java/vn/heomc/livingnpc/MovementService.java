package vn.heomc.livingnpc;

import org.bukkit.Location;
import net.citizensnpcs.api.ai.Navigator;

/** Cua vao duy nhat cho validation movement intent truoc khi chay Citizens. */
final class MovementService {
    private MovementService() {
    }

    static MovementIntent intentFor(FarmerPhase phase) {
        if (phase == null) return null;
        return switch (phase) {
            case GOING_HOME, SHELTERING -> MovementIntent.GOING_HOME;
            case GOING_TO_BED -> MovementIntent.GOING_TO_BED;
            case GOING_TO_PLOT, GOING_TO_CROP, RETURNING_TO_PLOT -> MovementIntent.GOING_TO_PLOT;
            case GOING_TO_STORAGE -> MovementIntent.GO_TO_STORAGE;
            case GOING_TO_WORK_STATION -> MovementIntent.GOING_TO_WORK_STATION;
            case GOING_TO_FISHING_SPOT -> MovementIntent.GOING_TO_FISHING_SPOT;
            case GOING_TO_STALL -> MovementIntent.GOING_TO_STALL;
            case WANDERING -> MovementIntent.WANDERING;
            default -> null;
        };
    }

    static boolean valid(FarmerPhase phase, Location current, Location target) {
        return MovementIntentPolicy.valid(intentFor(phase), current, target);
    }

    static boolean startSimpleNavigation(
            Navigator navigator, Location target, float speedModifier, double margin) {
        if (navigator == null || target == null
                || !Double.isFinite(target.getX()) || !Double.isFinite(target.getY())
                || !Double.isFinite(target.getZ()) || !Float.isFinite(speedModifier) || speedModifier <= 0.0F
                || !Double.isFinite(margin) || margin < 0.0) {
            return false;
        }
        boolean sameTarget = navigator.isNavigating() && navigator.getTargetAsLocation() != null
                && (navigator.getTargetAsLocation().getWorld() == null
                    || target.getWorld() == null
                    || navigator.getTargetAsLocation().getWorld() == target.getWorld())
                && navigator.getTargetAsLocation().distanceSquared(target) <= 0.0001;
        if (!sameTarget) navigator.setTarget(target);
        LivingNavigation.allowDoors(navigator.getLocalParameters())
                .speedModifier(speedModifier)
                .distanceMargin(margin)
                .pathDistanceMargin(margin)
                .destinationTeleportMargin(0.0)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
        return true;
    }
}
