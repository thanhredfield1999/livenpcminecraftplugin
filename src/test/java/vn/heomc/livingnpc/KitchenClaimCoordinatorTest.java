package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KitchenClaimCoordinatorTest {
    private static final StoredLocation PANTRY = location(1, 64, 1);
    private static final StoredLocation PREP = location(2, 64, 1);
    private static final StoredLocation SERVING = location(3, 64, 1);

    @Test
    void preventsTwoKitchensFromLinkingTheSameBlock() {
        KitchenClaimCoordinator coordinator = new KitchenClaimCoordinator();
        StoredLocation furnace = location(0, 64, 0);

        assertTrue(coordinator.register(kitchen("kitchen_a", "furnace_a", furnace)));
        assertFalse(coordinator.register(kitchen("kitchen_b", "furnace_b", furnace)));
    }

    @Test
    void applianceHasOneSessionOwnerAtATime() {
        KitchenClaimCoordinator coordinator = new KitchenClaimCoordinator();
        coordinator.register(kitchen("kitchen_a", "furnace_a", location(0, 64, 0)));
        UUID firstCook = UUID.randomUUID();

        assertTrue(coordinator.claim("kitchen_a", "furnace_a", firstCook, "session-1"));
        assertTrue(coordinator.claim("kitchen_a", "furnace_a", firstCook, "session-1"));
        assertFalse(coordinator.claim("kitchen_a", "furnace_a", UUID.randomUUID(), "session-2"));

        coordinator.release("session-1");
        assertTrue(coordinator.claim("kitchen_a", "furnace_a", UUID.randomUUID(), "session-2"));
    }

    private KitchenDefinition kitchen(String id, String applianceId, StoredLocation block) {
        return new KitchenDefinition(id, "village", List.of(
                new KitchenAppliance(applianceId, KitchenApplianceType.FURNACE, block)),
                PANTRY, PREP, SERVING, null);
    }

    private static StoredLocation location(double x, double y, double z) {
        return new StoredLocation("world", x, y, z, 0.0f, 0.0f);
    }
}
