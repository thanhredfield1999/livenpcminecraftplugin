package vn.heomc.livingnpc;

import java.util.Map;
import java.util.UUID;

record CookingSession(
        UUID sessionId,
        String villageId,
        UUID cookUuid,
        String applianceId,
        CookingApplianceKey appliance,
        String recipeId,
        Map<String, Integer> reserved,
        Map<String, Integer> loaded,
        Map<String, Integer> consumed,
        Map<String, Integer> residual,
        Map<String, Integer> produced,
        Map<Integer, String> slotSnapshots,
        long startedActiveTick,
        long elapsedActiveTicks,
        long requiredActiveTicks,
        CookingPhase phase) {
    CookingSession {
        if (sessionId == null || cookUuid == null || appliance == null || phase == null
                || blank(villageId) || blank(applianceId) || blank(recipeId)
                || startedActiveTick < 0L || elapsedActiveTicks < 0L || requiredActiveTicks <= 0L
                || elapsedActiveTicks > requiredActiveTicks) {
            throw new IllegalArgumentException("Invalid cooking session");
        }
        reserved = quantities(reserved);
        loaded = quantities(loaded);
        consumed = quantities(consumed);
        residual = quantities(residual);
        produced = quantities(produced);
        slotSnapshots = slotSnapshots == null ? Map.of() : Map.copyOf(slotSnapshots);
    }

    boolean active() {
        return phase.active();
    }

    CookingSession transition(CookingPhase next) {
        if (!phase.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid cooking transition " + phase + " -> " + next);
        }
        return new CookingSession(sessionId, villageId, cookUuid, applianceId, appliance, recipeId,
                reserved, loaded, consumed, residual, produced, slotSnapshots,
                startedActiveTick, elapsedActiveTicks, requiredActiveTicks, next);
    }

    private static Map<String, Integer> quantities(Map<String, Integer> values) {
        if (values == null) return Map.of();
        if (values.entrySet().stream().anyMatch(entry -> blank(entry.getKey())
                || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("Invalid cooking quantities");
        }
        return Map.copyOf(values);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
