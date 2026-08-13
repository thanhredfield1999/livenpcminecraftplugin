package vn.heomc.livingnpc;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

final class MealRequestBoard {
    private final Map<String, MealRequest> requests = new HashMap<>();

    MealRequest update(
            String villageId, ProductionRecipe recipe, int waitingResidents,
            int currentStock, int maxBatch, long serverTick) {
        if (villageId == null || villageId.isBlank() || recipe == null || !recipe.cookingRecipe()) return null;
        String key = key(villageId, recipe.id());
        int stockCapacity = Math.max(0, recipe.stockTarget() - Math.max(0, currentStock)) / recipe.outputAmount();
        int demand = Math.ceilDiv(Math.max(0, waitingResidents), recipe.servings());
        int batches = Math.min(Math.min(demand, Math.max(1, maxBatch)), stockCapacity);
        MealRequest current = requests.get(key);
        if (batches <= 0) {
            if (current == null || current.claimedBy() == null) requests.remove(key);
            return null;
        }
        MealRequest updated = new MealRequest(
                villageId, recipe.id(), waitingResidents, batches, recipe.priority(), serverTick,
                current == null ? null : current.claimedBy());
        requests.put(key, updated);
        return updated;
    }

    MealRequest claimBest(String villageId, UUID cookUuid, Predicate<MealRequest> available) {
        if (villageId == null || cookUuid == null) return null;
        MealRequest selected = requests.values().stream()
                .filter(request -> request.villageId().equals(villageId) && request.claimedBy() == null)
                .filter(available == null ? ignored -> true : available)
                .max(Comparator.comparingInt(MealRequest::priority)
                        .thenComparingInt(MealRequest::waitingResidents)
                        .thenComparingLong(request -> -request.updatedTick()))
                .orElse(null);
        if (selected == null) return null;
        MealRequest claimed = selected.claim(cookUuid);
        requests.put(key(selected.villageId(), selected.recipeId()), claimed);
        return claimed;
    }

    void release(UUID cookUuid) {
        if (cookUuid == null) return;
        requests.replaceAll((key, request) -> cookUuid.equals(request.claimedBy()) ? request.claim(null) : request);
    }

    void complete(String villageId, String recipeId, UUID cookUuid) {
        String key = key(villageId, recipeId);
        MealRequest current = requests.get(key);
        if (current != null && cookUuid != null && cookUuid.equals(current.claimedBy())) requests.remove(key);
    }

    List<MealRequest> requests(String villageId) {
        return requests.values().stream().filter(request -> request.villageId().equals(villageId)).toList();
    }

    private String key(String villageId, String recipeId) {
        return villageId + '/' + recipeId;
    }
}
