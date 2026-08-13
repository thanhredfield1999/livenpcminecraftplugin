package vn.heomc.livingnpc;

import java.util.Set;
import java.util.UUID;

record ActivityPoint(
        String id,
        ActivityPointType type,
        StoredLocation interaction,
        StoredLocation standing,
        int capacity,
        Long openTick,
        Long closeTick,
        Set<ResidentRole> allowedRoles,
        UUID assignedNpcUuid) {

    ActivityPoint {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Activity point ID is required");
        if (type == null || interaction == null || standing == null) {
            throw new IllegalArgumentException("Activity point type and locations are required");
        }
        capacity = Math.clamp(capacity, 1, 64);
        openTick = openTick == null ? null : Math.floorMod(openTick, 24_000L);
        closeTick = closeTick == null ? null : Math.floorMod(closeTick, 24_000L);
        allowedRoles = allowedRoles == null ? Set.of() : Set.copyOf(allowedRoles);
    }

    boolean availableAt(long worldTick, ResidentRole role, UUID npcUuid) {
        if (assignedNpcUuid != null && !assignedNpcUuid.equals(npcUuid)) return false;
        if (!allowedRoles.isEmpty() && !allowedRoles.contains(role)) return false;
        if (openTick == null || closeTick == null || openTick.equals(closeTick)) return true;
        return SchedulePolicy.isScheduledTime(
                Math.floorMod(worldTick, 24_000L), new ResidentSchedule(openTick, closeTick));
    }

    String resourceId() {
        return "activity:" + id;
    }
}
