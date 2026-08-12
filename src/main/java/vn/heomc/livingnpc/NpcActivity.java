package vn.heomc.livingnpc;

import java.time.Instant;
import java.util.UUID;

record NpcActivity(
        UUID npcUuid, String villageId, ResidentRole role, String action,
        String itemKey, int amount, Instant createdAt) {
}
