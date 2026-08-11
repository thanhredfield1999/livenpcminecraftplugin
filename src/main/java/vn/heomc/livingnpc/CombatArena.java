package vn.heomc.livingnpc;

import java.util.UUID;
import org.bukkit.Location;

record CombatArena(
        String id,
        String villageId,
        UUID archerUuid,
        UUID swordsmanUuid,
        StoredLocation firstCorner,
        StoredLocation secondCorner,
        StoredLocation retreatPoint,
        boolean active,
        int killsThisRun) {

    boolean configured() {
        return firstCorner != null && secondCorner != null && retreatPoint != null
                && firstCorner.world().equals(secondCorner.world())
                && firstCorner.world().equals(retreatPoint.world());
    }

    boolean contains(Location location) {
        if (!configured() || location == null || location.getWorld() == null
                || !firstCorner.world().equals(location.getWorld().getName())) {
            return false;
        }
        return between(location.getX(), firstCorner.x(), secondCorner.x())
                && between(location.getY(), firstCorner.y(), secondCorner.y())
                && between(location.getZ(), firstCorner.z(), secondCorner.z());
    }

    CombatArena withCorner(int corner, StoredLocation location) {
        return corner == 1
                ? new CombatArena(id, villageId, archerUuid, swordsmanUuid, location, secondCorner,
                        retreatPoint, false, 0)
                : new CombatArena(id, villageId, archerUuid, swordsmanUuid, firstCorner, location,
                        retreatPoint, false, 0);
    }

    CombatArena withRetreatPoint(StoredLocation location) {
        return new CombatArena(id, villageId, archerUuid, swordsmanUuid, firstCorner, secondCorner,
                location, false, 0);
    }

    CombatArena withActive(boolean value) {
        return new CombatArena(id, villageId, archerUuid, swordsmanUuid, firstCorner, secondCorner,
                retreatPoint, value, value ? 0 : killsThisRun);
    }

    CombatArena withKills(int kills) {
        return new CombatArena(id, villageId, archerUuid, swordsmanUuid, firstCorner, secondCorner,
                retreatPoint, active, Math.max(0, kills));
    }

    private boolean between(double value, double first, double second) {
        return value >= Math.min(first, second) && value <= Math.max(first, second);
    }
}
