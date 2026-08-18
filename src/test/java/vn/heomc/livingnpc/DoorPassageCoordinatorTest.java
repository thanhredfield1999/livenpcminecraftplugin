package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DoorPassageCoordinatorTest {
    private static final DoorPassageCoordinator.DoorKey DOOR =
            new DoorPassageCoordinator.DoorKey("world", 10, 64, 20);

    @Test
    void threeRequestsGiveOneOwnerAndTwoWaitingInFifoOrder() {
        List<UUID> started = new ArrayList<>();
        DoorPassageCoordinator c = new DoorPassageCoordinator(8, (npc, result) -> {});
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), d = UUID.randomUUID();
        assertEquals(DoorPassageCoordinator.Result.OWNER, c.request(DOOR, a, () -> started.add(a)));
        assertEquals(DoorPassageCoordinator.Result.WAITING, c.request(DOOR, b, () -> started.add(b)));
        assertEquals(DoorPassageCoordinator.Result.WAITING, c.request(DOOR, d, () -> started.add(d)));
        assertTrue(c.owns(DOOR, a));
        assertEquals(2, c.waitingCount(DOOR));
        assertEquals(List.of(), started);
    }

    @Test
    void threeNpcGateRequestsShareOnePhysicalGateQueue() {
        DoorPassageCoordinator c = new DoorPassageCoordinator();
        DoorPassageCoordinator.DoorKey gate = new DoorPassageCoordinator.DoorKey("world", 30, 64, 40);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();

        assertEquals(DoorPassageCoordinator.Result.OWNER, c.request(gate, first, () -> {}));
        assertEquals(DoorPassageCoordinator.Result.WAITING, c.request(gate, second, () -> {}));
        assertEquals(DoorPassageCoordinator.Result.WAITING, c.request(gate, third, () -> {}));
        assertEquals(1, c.ownerCount());
        assertEquals(2, c.waitingCount(gate));
        assertTrue(c.owns(gate, first));
    }

    @Test
    void releaseStartsNextOwnerAndPreservesFifo() {
        List<UUID> started = new ArrayList<>();
        DoorPassageCoordinator c = new DoorPassageCoordinator();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), d = UUID.randomUUID();
        c.request(DOOR, a, () -> started.add(a));
        c.request(DOOR, b, () -> started.add(b));
        c.request(DOOR, d, () -> started.add(d));
        c.release(DOOR, a, "RESUMED");
        assertEquals(List.of(b), started);
        assertTrue(c.owns(DOOR, b));
        c.release(DOOR, b, "ABORTED");
        assertEquals(List.of(b, d), started);
        assertTrue(c.owns(DOOR, d));
    }

    @Test
    void ownerCleanupLeavesNoLeak() {
        DoorPassageCoordinator c = new DoorPassageCoordinator();
        UUID a = UUID.randomUUID();
        c.request(DOOR, a, () -> {});
        c.release(DOOR, a, "ABORTED_TIMEOUT");
        assertEquals(0, c.ownerCount());
        assertEquals(0, c.trackedCount());
        assertEquals(0, c.waitingCount(DOOR));
    }

    @Test
    void duplicateRequestIsIdempotentAndDoesNotEnqueueTwice() {
        DoorPassageCoordinator c = new DoorPassageCoordinator();
        UUID a = UUID.randomUUID();
        assertEquals(DoorPassageCoordinator.Result.OWNER, c.request(DOOR, a, () -> {}));
        assertEquals(DoorPassageCoordinator.Result.DUPLICATE, c.request(DOOR, a, () -> {}));
        assertEquals(1, c.trackedCount());
        assertEquals(0, c.waitingCount(DOOR));
    }

    @Test
    void boundedQueueRejectsBeyondLimit() {
        DoorPassageCoordinator c = new DoorPassageCoordinator(2, (npc, result) -> {});
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), d = UUID.randomUUID(), e = UUID.randomUUID();
        c.request(DOOR, a, () -> {});
        assertEquals(DoorPassageCoordinator.Result.WAITING, c.request(DOOR, b, () -> {}));
        assertEquals(DoorPassageCoordinator.Result.WAITING, c.request(DOOR, d, () -> {}));
        assertEquals(DoorPassageCoordinator.Result.REJECTED, c.request(DOOR, e, () -> {}));
        assertEquals(2, c.waitingCount(DOOR));
        assertEquals(3, c.trackedCount());
    }

    @Test
    void shutdownClearsOwnersAndWaiters() {
        DoorPassageCoordinator c = new DoorPassageCoordinator();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        c.request(DOOR, a, () -> {});
        c.request(DOOR, b, () -> {});
        c.shutdown();
        assertEquals(0, c.trackedCount());
        assertEquals(0, c.ownerCount());
    }

    @Test
    void sequentialConfiguredGatesReleaseFirstOwnerBeforeSecondClaimAndIgnoreStaleRelease() {
        DoorPassageCoordinator c = new DoorPassageCoordinator();
        DoorPassageCoordinator.DoorKey firstGate = new DoorPassageCoordinator.DoorKey("world", 10, 64, 20);
        DoorPassageCoordinator.DoorKey secondGate = new DoorPassageCoordinator.DoorKey("world", 30, 64, 40);
        UUID npc = UUID.randomUUID();
        List<String> opened = new ArrayList<>();

        assertEquals(DoorPassageCoordinator.Result.OWNER,
                c.request(firstGate, npc, () -> opened.add("gate-1")));
        c.release(firstGate, npc, "RESUMED_GATE");

        assertEquals(0, c.ownerCount());
        assertEquals(0, c.trackedCount());
        assertEquals(DoorPassageCoordinator.Result.OWNER,
                c.request(secondGate, npc, () -> opened.add("gate-2")));
        assertTrue(c.owns(secondGate, npc));
        assertEquals(1, c.ownerCount());

        c.release(firstGate, npc, "STALE_GATE_1_CALLBACK");
        assertTrue(c.owns(secondGate, npc));
        assertEquals(1, c.ownerCount());

        c.release(secondGate, npc, "RESUMED_GATE");
        assertEquals(0, c.ownerCount());
        assertEquals(0, c.trackedCount());
        assertEquals(List.of(), opened);
    }
}
