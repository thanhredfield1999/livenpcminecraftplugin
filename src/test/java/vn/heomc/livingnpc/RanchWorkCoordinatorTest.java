package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RanchWorkCoordinatorTest {
    @Test
    void onlyOneNpcCanOwnTheSameVillageRanchAtATime() {
        RanchWorkCoordinator coordinator = new RanchWorkCoordinator();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        StoredLocation ranch = new StoredLocation("world", 0, 64, 0, 0, 0);

        assertTrue(coordinator.acquire("village", first, ranch, 6));
        assertTrue(coordinator.acquire("village", first, ranch, 6));
        assertFalse(coordinator.acquire("village", second, ranch, 6));

        coordinator.release(first);
        assertTrue(coordinator.acquire("village", second, ranch, 6));
    }

    @Test
    void overlappingRanchesAcrossVillagesShareTheSameLock() {
        RanchWorkCoordinator coordinator = new RanchWorkCoordinator();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(coordinator.acquire(
                "north", first, new StoredLocation("world", 0, 64, 0, 0, 0), 6));
        assertFalse(coordinator.acquire(
                "south", second, new StoredLocation("world", 10, 64, 0, 0, 0), 6));
        assertTrue(coordinator.acquire(
                "far", second, new StoredLocation("world", 20, 64, 0, 0, 0), 6));
    }

    @Test
    void ranchesInDifferentWorldsDoNotConflict() {
        RanchWorkCoordinator coordinator = new RanchWorkCoordinator();

        assertTrue(coordinator.acquire(
                "north", UUID.randomUUID(), new StoredLocation("world", 0, 64, 0, 0, 0), 6));
        assertTrue(coordinator.acquire(
                "south", UUID.randomUUID(), new StoredLocation("other", 0, 64, 0, 0, 0), 6));
    }
}
