package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MiningWorkCoordinatorTest {
    private final MiningWorkCoordinator coordinator = new MiningWorkCoordinator();

    @Test
    void preventsTwoMinersFromClaimingTheSameVillageZone() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(claim(first, "village_a", "mine_1", 10));
        assertFalse(claim(second, "village_a", "mine_1", 11));

        coordinator.release(first);
        assertTrue(claim(second, "village_a", "mine_1", 11));
    }

    @Test
    void identicalZoneIdsInDifferentVillagesDoNotConflict() {
        assertTrue(claim(UUID.randomUUID(), "village_a", "mine_1", 10));
        assertTrue(claim(UUID.randomUUID(), "village_b", "mine_1", 20));
    }

    @Test
    void preventsTwoMinersFromReservingTheSameBlock() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(claim(first, "village_a", "mine_1", 10));
        assertFalse(claim(second, "village_a", "mine_2", 10));

        coordinator.release(first);
        assertTrue(claim(second, "village_a", "mine_2", 10));
    }

    @Test
    void oneMinerCanMoveItsReservationToAnotherZone() {
        UUID miner = UUID.randomUUID();

        assertTrue(claim(miner, "village_a", "mine_1", 10));
        assertTrue(claim(miner, "village_a", "mine_2", 20));
        assertTrue(claim(UUID.randomUUID(), "village_a", "mine_1", 10));
    }

    @Test
    void pathFailureBackoffIsScopedToMinerAndZone() {
        UUID miner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        coordinator.backoff(miner, "village_a", "mine_1", 200L);

        assertTrue(coordinator.isBackedOff(miner, "village_a", "mine_1", 199L));
        assertFalse(coordinator.isBackedOff(miner, "village_a", "mine_2", 199L));
        assertFalse(coordinator.isBackedOff(other, "village_a", "mine_1", 199L));
        assertFalse(coordinator.isBackedOff(miner, "village_a", "mine_1", 200L));
    }

    @Test
    void clearRemovesClaimAndBackoff() {
        UUID miner = UUID.randomUUID();
        assertTrue(claim(miner, "village_a", "mine_1", 10));
        coordinator.backoff(miner, "village_a", "mine_2", 200L);

        coordinator.clear(miner);

        assertTrue(claim(UUID.randomUUID(), "village_a", "mine_1", 10));
        assertFalse(coordinator.isBackedOff(miner, "village_a", "mine_2", 100L));
    }

    @Test
    void temporaryRestorationBlocksAreNotMineableOutputs() {
        assertTrue(CivilProfessionRuntime.miningOutput(Material.STONE).isPresent());
        assertTrue(CivilProfessionRuntime.miningOutput(Material.DEEPSLATE_IRON_ORE).isPresent());
        assertFalse(CivilProfessionRuntime.miningOutput(Material.COBBLESTONE).isPresent());
        assertFalse(CivilProfessionRuntime.miningOutput(Material.COBBLED_DEEPSLATE).isPresent());
    }

    private boolean claim(UUID npcUuid, String villageId, String zoneId, int blockX) {
        return coordinator.claim(npcUuid, villageId, zoneId, "StillCliff", blockX, 50, 10);
    }
}
