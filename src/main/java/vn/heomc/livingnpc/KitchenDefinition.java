package vn.heomc.livingnpc;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

record KitchenDefinition(
        String id,
        String villageId,
        List<KitchenAppliance> appliances,
        StoredLocation pantry,
        StoredLocation prep,
        StoredLocation serving,
        StoredLocation water) {
    KitchenDefinition {
        if (id == null || !id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid kitchen id");
        if (villageId == null || villageId.isBlank()) throw new IllegalArgumentException("Village is required");
        appliances = List.copyOf(appliances);
        if (appliances.isEmpty() || pantry == null || prep == null || serving == null) {
            throw new IllegalArgumentException("Kitchen requires appliance, pantry, prep and serving points");
        }
        Set<String> ids = appliances.stream().map(KitchenAppliance::id).collect(Collectors.toSet());
        Set<String> blocks = appliances.stream().map(KitchenAppliance::blockKey).collect(Collectors.toSet());
        if (ids.size() != appliances.size() || blocks.size() != appliances.size()) {
            throw new IllegalArgumentException("Kitchen appliance ids and blocks must be unique");
        }
        if (appliances.stream().anyMatch(appliance -> !appliance.block().world().equals(pantry.world()))
                || !prep.world().equals(pantry.world()) || !serving.world().equals(pantry.world())
                || water != null && !water.world().equals(pantry.world())) {
            throw new IllegalArgumentException("Kitchen points must be in one world");
        }
    }
}
