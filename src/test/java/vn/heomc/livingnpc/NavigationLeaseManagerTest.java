package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class NavigationLeaseManagerTest {
    @Test
    void onlyHigherPriorityAuthorityCanPreemptCurrentOwner() {
        NavigationLeaseManager leases = new NavigationLeaseManager();
        UUID npc = UUID.randomUUID();
        AtomicBoolean preempted = new AtomicBoolean();

        assertTrue(leases.claim(npc, "resident-role", 30, () -> preempted.set(true)));
        assertFalse(leases.claim(npc, "social", 20, null));
        assertTrue(leases.heldBy(npc, "resident-role"));
        assertTrue(leases.claim(npc, "sleep", 80, null));
        assertTrue(preempted.get());
        assertTrue(leases.heldBy(npc, "sleep"));
    }

    @Test
    void onlyTheOwnerCanReleaseALease() {
        NavigationLeaseManager leases = new NavigationLeaseManager();
        UUID npc = UUID.randomUUID();

        leases.claim(npc, "morning-exit", 70, null);
        leases.release(npc, "resident-role");
        assertTrue(leases.heldBy(npc, "morning-exit"));
        leases.release(npc, "morning-exit");
        assertFalse(leases.heldBy(npc, "morning-exit"));
    }

    @Test
    void doorPassagePreemptsFisherAndBlocksReclaimUntilRelease() {
        NavigationLeaseManager leases = new NavigationLeaseManager();
        UUID npc = UUID.randomUUID();
        FisherRuntime.NavigationPause fisherPause = new FisherRuntime.NavigationPause();

        assertTrue(FisherRuntime.claimNavigation(leases, npc, fisherPause::pause));
        assertTrue(DoubleDoorListener.claimPassage(leases, npc));

        assertTrue(fisherPause.paused());
        assertTrue(leases.heldBy(npc, DoubleDoorListener.NAVIGATION_OWNER));
        assertFalse(FisherRuntime.claimNavigation(leases, npc, null));

        DoubleDoorListener.releasePassage(leases, npc);

        assertTrue(FisherRuntime.claimNavigation(leases, npc, null));
        assertTrue(fisherPause.resume());
        assertFalse(fisherPause.paused());
    }

    @Test
    void doorPassageDetectsHigherPriorityOwnerEvenBeforeTargetChanges() {
        NavigationLeaseManager leases = new NavigationLeaseManager();
        UUID npc = UUID.randomUUID();

        assertTrue(DoubleDoorListener.claimPassage(leases, npc));
        assertTrue(leases.claim(npc, "emergency", 100, null));

        assertFalse(DoubleDoorListener.ownsPassage(leases, npc));
        assertTrue(leases.heldBy(npc, "emergency"));
    }

    @Test
    void failingPreemptCallbackCannotKeepTheOldNavigationOwner() {
        NavigationLeaseManager leases = new NavigationLeaseManager();
        UUID npc = UUID.randomUUID();
        AtomicBoolean callbackSawNewOwner = new AtomicBoolean();
        assertTrue(leases.claim(npc, "fisher", 30, () -> {
            callbackSawNewOwner.set(leases.heldBy(npc, "door"));
            throw new IllegalStateException("pause callback failed");
        }));

        assertDoesNotThrow(() -> assertTrue(leases.claim(npc, "door", 90, null)));

        assertTrue(callbackSawNewOwner.get());
        assertTrue(leases.heldBy(npc, "door"));
        assertFalse(leases.heldBy(npc, "fisher"));
    }
}
