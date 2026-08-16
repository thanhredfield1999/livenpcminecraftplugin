package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class MiningWorkCoordinator {
    private final Map<UUID, WorkClaim> claims = new HashMap<>();
    private final Map<BackoffKey, Long> backoffs = new HashMap<>();

    boolean claim(
            UUID npcUuid, String villageId, String zoneId,
            String world, int blockX, int blockY, int blockZ) {
        WorkClaim requested = new WorkClaim(
                new ZoneKey(villageId, zoneId), new BlockKey(world, blockX, blockY, blockZ));
        WorkClaim current = claims.get(npcUuid);
        if (requested.equals(current)) return true;
        if (claims.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(npcUuid)
                && (entry.getValue().zone().equals(requested.zone())
                || entry.getValue().block().equals(requested.block())))) return false;
        claims.put(npcUuid, requested);
        return true;
    }

    void release(UUID npcUuid) {
        claims.remove(npcUuid);
    }

    void backoff(UUID npcUuid, String villageId, String zoneId, long retryAtTick) {
        backoffs.put(new BackoffKey(npcUuid, new ZoneKey(villageId, zoneId)), retryAtTick);
    }

    boolean isBackedOff(UUID npcUuid, String villageId, String zoneId, long serverTick) {
        BackoffKey key = new BackoffKey(npcUuid, new ZoneKey(villageId, zoneId));
        Long retryAt = backoffs.get(key);
        if (retryAt == null) return false;
        if (serverTick < retryAt) return true;
        backoffs.remove(key);
        return false;
    }

    void clear(UUID npcUuid) {
        release(npcUuid);
        backoffs.keySet().removeIf(key -> key.npcUuid().equals(npcUuid));
    }

    private record ZoneKey(String villageId, String zoneId) {
    }

    private record BlockKey(String world, int x, int y, int z) {
    }

    private record WorkClaim(ZoneKey zone, BlockKey block) {
    }

    private record BackoffKey(UUID npcUuid, ZoneKey zone) {
    }
}
