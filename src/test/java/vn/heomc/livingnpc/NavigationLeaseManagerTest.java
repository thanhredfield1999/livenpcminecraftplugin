package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
