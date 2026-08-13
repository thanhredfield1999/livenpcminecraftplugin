package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class KitchenClaimCoordinator {
    private final Map<String, KitchenAppliance> appliances = new HashMap<>();
    private final Map<String, String> applianceOwners = new HashMap<>();
    private final Map<String, Claim> claims = new HashMap<>();

    boolean register(KitchenDefinition kitchen) {
        if (kitchen == null || kitchen.appliances().stream().anyMatch(appliance ->
                applianceOwners.containsKey(appliance.blockKey())
                        && !kitchen.id().equals(applianceOwners.get(appliance.blockKey())))) return false;
        unregister(kitchen.id());
        for (KitchenAppliance appliance : kitchen.appliances()) {
            String key = applianceKey(kitchen.id(), appliance.id());
            appliances.put(key, appliance);
            applianceOwners.put(appliance.blockKey(), kitchen.id());
        }
        return true;
    }

    boolean claim(String kitchenId, String applianceId, UUID cookUuid, String sessionId) {
        if (cookUuid == null || sessionId == null || sessionId.isBlank()) return false;
        String key = applianceKey(kitchenId, applianceId);
        if (!appliances.containsKey(key)) return false;
        Claim requested = new Claim(cookUuid, sessionId);
        Claim current = claims.get(key);
        if (current != null) return current.equals(requested);
        if (claims.values().stream().anyMatch(claim -> claim.sessionId().equals(sessionId))) return false;
        claims.put(key, requested);
        return true;
    }

    void release(String sessionId) {
        if (sessionId != null) claims.entrySet().removeIf(entry -> entry.getValue().sessionId().equals(sessionId));
    }

    void releaseCook(UUID cookUuid) {
        if (cookUuid != null) claims.entrySet().removeIf(entry -> entry.getValue().cookUuid().equals(cookUuid));
    }

    void unregister(String kitchenId) {
        if (kitchenId == null) return;
        appliances.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(kitchenId + '/')) return false;
            applianceOwners.remove(entry.getValue().blockKey(), kitchenId);
            claims.remove(entry.getKey());
            return true;
        });
    }

    private String applianceKey(String kitchenId, String applianceId) {
        return kitchenId + '/' + applianceId;
    }

    private record Claim(UUID cookUuid, String sessionId) {
    }
}
