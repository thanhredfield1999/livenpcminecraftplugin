package vn.heomc.livingnpc;

/** Y dinh di chuyen domain; movement core khong tu suy dien tu geometry. */
enum MovementIntent {
    GOING_HOME,
    GOING_TO_BED,
    GOING_TO_PLOT,
    GO_TO_STORAGE,
    GOING_TO_WORK_STATION,
    GOING_TO_FISHING_SPOT,
    GOING_TO_STALL,
    WANDERING
}

final class MovementIntentPolicy {
    private MovementIntentPolicy() {
    }

    static boolean requiresConfiguredGate(MovementIntent intent) {
        return intent != null && switch (intent) {
            case GOING_HOME, GOING_TO_BED, GOING_TO_PLOT, GO_TO_STORAGE, GOING_TO_WORK_STATION -> true;
            case GOING_TO_FISHING_SPOT, GOING_TO_STALL, WANDERING -> false;
        };
    }

    static boolean valid(MovementIntent intent, org.bukkit.Location current, org.bukkit.Location target) {
        return intent != null && current != null && target != null
                && current.getWorld() != null && current.getWorld().equals(target.getWorld())
                && target.getX() == target.getX() && target.getY() == target.getY() && target.getZ() == target.getZ();
    }
}
