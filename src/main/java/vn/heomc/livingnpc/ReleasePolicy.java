package vn.heomc.livingnpc;

import java.util.EnumSet;
import java.util.Set;

final class ReleasePolicy {
    static final int SEASON = 2;
    private static final Set<ResidentRole> ENABLED_ROLES =
            EnumSet.of(ResidentRole.RESIDENT, ResidentRole.FARMER, ResidentRole.FISHER, ResidentRole.RANCHER);

    private ReleasePolicy() {
    }

    static boolean roleEnabled(ResidentRole role) {
        return role != null && ENABLED_ROLES.contains(role);
    }

    static Set<ResidentRole> enabledRoles() {
        return Set.copyOf(ENABLED_ROLES);
    }

    static boolean workZoneEnabled(VillageWorkZoneType type) {
        return type == VillageWorkZoneType.FISHING
                || type == VillageWorkZoneType.RANCH
                || type == VillageWorkZoneType.MINING;
    }

    static boolean seasonTwoRuntimesEnabled() {
        return SEASON >= 2;
    }

    static boolean seasonThreeRuntimesEnabled() {
        return SEASON >= 3;
    }

    static boolean seasonFourRuntimesEnabled() {
        return SEASON >= 4;
    }

    static boolean seasonNineRuntimesEnabled() {
        return SEASON >= 9;
    }
}
