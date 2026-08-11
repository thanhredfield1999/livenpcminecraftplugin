package vn.heomc.livingnpc;

import java.util.Map;
import java.util.Set;

final class ActiveRolePolicy {
    private ActiveRolePolicy() {
    }

    static ResidentRole select(
            Set<ResidentRole> assignedRoles,
            ResidentRole currentRole,
            Map<ResidentRole, ResidentSchedule> schedules,
            ResidentSchedule fallback,
            long worldTime) {
        if (assignedRoles.contains(currentRole)
                && isScheduled(currentRole, schedules, fallback, worldTime)) {
            return currentRole;
        }
        for (ResidentRole role : ResidentRole.values()) {
            if (assignedRoles.contains(role) && isScheduled(role, schedules, fallback, worldTime)) {
                return role;
            }
        }
        return currentRole;
    }

    private static boolean isScheduled(
            ResidentRole role,
            Map<ResidentRole, ResidentSchedule> schedules,
            ResidentSchedule fallback,
            long worldTime) {
        return SchedulePolicy.isWorkTime(worldTime, false, schedules.getOrDefault(role, fallback));
    }
}
