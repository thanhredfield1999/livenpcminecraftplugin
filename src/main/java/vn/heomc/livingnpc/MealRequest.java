package vn.heomc.livingnpc;

import java.util.UUID;

record MealRequest(
        String villageId,
        String recipeId,
        int waitingResidents,
        int batches,
        int priority,
        long updatedTick,
        UUID claimedBy) {
    MealRequest claim(UUID cookUuid) {
        return new MealRequest(villageId, recipeId, waitingResidents, batches, priority, updatedTick, cookUuid);
    }
}
