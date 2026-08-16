package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class MinerCandidateSelectionTest {
    @Test
    void blockedNearZoneFallsBackToReachableFarZone() {
        MiningZone near = zone("mine_1", 10);
        MiningZone far = zone("mine_2", 30);
        List<CivilProfessionRuntime.MiningCandidate> candidates = List.of(
                candidate(near, 1.0), candidate(near, 2.0), candidate(near, 3.0), candidate(near, 4.0),
                candidate(far, 20.0));
        AtomicInteger pathChecks = new AtomicInteger();

        CivilProfessionRuntime.MiningSelection result = CivilProfessionRuntime.selectMiningCandidate(
                candidates, 4, false,
                candidate -> {
                    pathChecks.incrementAndGet();
                    return candidate.zone().equals(far);
                }, candidate -> true);

        assertSame(far, result.selected().zone());
        assertEquals(2, pathChecks.get());
        assertTrue(result.routeFailures().contains(near));
    }

    @Test
    void pathChecksNeverExceedFourCandidates() {
        List<CivilProfessionRuntime.MiningCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 10; index++) candidates.add(candidate(zone("mine_" + index, index), index));
        AtomicInteger pathChecks = new AtomicInteger();

        CivilProfessionRuntime.MiningSelection result = CivilProfessionRuntime.selectMiningCandidate(
                candidates, 4, false, candidate -> {
                    pathChecks.incrementAndGet();
                    return false;
                }, candidate -> true);

        assertEquals(4, pathChecks.get());
        assertEquals(4, result.pathCandidateCount());
        assertEquals(4, result.routeFailures().size());
    }

    @Test
    void claimedCandidateIsSkippedWithoutMarkingRouteFailure() {
        MiningZone first = zone("mine_1", 10);
        MiningZone second = zone("mine_2", 20);

        CivilProfessionRuntime.MiningSelection result = CivilProfessionRuntime.selectMiningCandidate(
                List.of(candidate(first, 1.0), candidate(second, 2.0)), 4, false,
                candidate -> true, candidate -> candidate.zone().equals(second));

        assertSame(second, result.selected().zone());
        assertTrue(result.routeFailures().isEmpty());
    }

    @Test
    void batchStopsExactlyAtConfiguredLimit() {
        assertFalse(CivilProfessionRuntime.batchComplete(0, 4));
        assertFalse(CivilProfessionRuntime.batchComplete(3, 4));
        assertTrue(CivilProfessionRuntime.batchComplete(4, 4));
        assertTrue(CivilProfessionRuntime.batchComplete(5, 4));
    }

    private CivilProfessionRuntime.MiningCandidate candidate(MiningZone zone, double distance) {
        return new CivilProfessionRuntime.MiningCandidate(
                mock(Block.class), zone, mock(Location.class), distance);
    }

    private MiningZone zone(String id, int x) {
        return new MiningZone(id, new StoredLocation("StillCliff", x, 50, 10, 0, 0), 48, 52);
    }
}
