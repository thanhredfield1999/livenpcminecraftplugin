package vn.heomc.livingnpc;

import java.util.UUID;

record PlacementSession(
        PlacementType type,
        UUID residentUuid,
        String profileId,
        String villageId,
        int plotRadius,
        long expiresAtMillis) {

    PlacementSession(
            PlacementType type, UUID residentUuid, String profileId,
            int plotRadius, long expiresAtMillis) {
        this(type, residentUuid, profileId, null, plotRadius, expiresAtMillis);
    }

    boolean expired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }
}
