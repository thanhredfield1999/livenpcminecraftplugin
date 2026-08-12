package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class RanchWorkCoordinator {
    private final Map<UUID, Claim> claims = new HashMap<>();

    boolean acquire(String villageId, UUID npcUuid, StoredLocation zone, int radius) {
        if (villageId == null || npcUuid == null || zone == null) return false;
        Claim current = claims.get(npcUuid);
        if (current != null) return true;
        Claim requested = new Claim(villageId, zone, radius);
        if (claims.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(npcUuid)
                && overlaps(entry.getValue(), requested))) return false;
        claims.put(npcUuid, requested);
        return true;
    }

    boolean occupiedByOther(String villageId, UUID npcUuid, StoredLocation zone, int radius) {
        Claim requested = new Claim(villageId, zone, radius);
        return claims.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(npcUuid)
                && overlaps(entry.getValue(), requested));
    }

    void release(UUID npcUuid) {
        if (npcUuid != null) claims.remove(npcUuid);
    }

    void clear() {
        claims.clear();
    }

    private boolean overlaps(Claim first, Claim second) {
        if (first.villageId().equals(second.villageId())) return true;
        if (!first.zone().world().equals(second.zone().world())) return false;
        int combined = first.radius() + second.radius();
        return Math.abs(first.zone().x() - second.zone().x()) <= combined
                && Math.abs(first.zone().z() - second.zone().z()) <= combined;
    }

    private record Claim(String villageId, StoredLocation zone, int radius) {
    }
}
