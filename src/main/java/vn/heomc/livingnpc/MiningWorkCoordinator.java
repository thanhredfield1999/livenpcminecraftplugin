package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class MiningWorkCoordinator {
    private final Map<UUID, String> claims = new HashMap<>();

    boolean claim(UUID npcUuid, String zoneId) {
        String current = claims.get(npcUuid);
        if (zoneId.equals(current)) return true;
        if (claims.containsValue(zoneId)) return false;
        claims.put(npcUuid, zoneId);
        return true;
    }

    void release(UUID npcUuid) {
        claims.remove(npcUuid);
    }
}
