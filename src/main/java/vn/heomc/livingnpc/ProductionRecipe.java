package vn.heomc.livingnpc;

import java.util.Map;

record ProductionRecipe(
        String id,
        ResidentRole role,
        Map<String, Integer> inputs,
        String output,
        int outputAmount,
        int stockTarget,
        String action,
        KitchenApplianceType appliance,
        Map<String, Integer> fuel,
        long cookTimeTicks,
        int servings,
        int nutrition,
        int hydration,
        int priority,
        String tool) {
    ProductionRecipe {
        inputs = Map.copyOf(inputs);
        fuel = Map.copyOf(fuel);
    }

    boolean cookingRecipe() {
        return role == ResidentRole.COOK;
    }
}
