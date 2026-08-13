package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Monster;

final class SecurityAlarmCoordinator {
    private static final long SCAN_INTERVAL_TICKS = 100L;
    private final Map<String, Snapshot> snapshots = new HashMap<>();

    Monster nearestDanger(String villageId, Location center, Location observer, long serverTick) {
        Snapshot snapshot = snapshots.get(villageId);
        if (snapshot == null || serverTick >= snapshot.expiresTick()) {
            Monster danger = center.getWorld().getNearbyEntitiesByType(Monster.class, center, 12.0).stream()
                    .min(java.util.Comparator.comparingDouble(monster -> observer.distanceSquared(monster.getLocation())))
                    .orElse(null);
            snapshot = new Snapshot(danger, serverTick + SCAN_INTERVAL_TICKS);
            snapshots.put(villageId, snapshot);
        }
        Monster danger = snapshot.danger();
        return danger != null && danger.isValid() && !danger.isDead() ? danger : null;
    }

    void clear() {
        snapshots.clear();
    }

    private record Snapshot(Monster danger, long expiresTick) {
    }
}
