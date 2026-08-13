package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CookingSessionStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsActiveLockBeforeReturningSuccess() {
        CookingSessionStore store = store();
        CookingSession session = session(UUID.randomUUID(), CookingPhase.RESERVED);

        assertTrue(store.create(session));
        assertTrue(store.locked(session.appliance()));

        CookingSessionStore reloaded = store();
        assertTrue(reloaded.writable());
        assertTrue(reloaded.locked(session.appliance()));
        assertEquals(session.sessionId(), reloaded.activeSession(session.appliance()).sessionId());
    }

    @Test
    void preventsTwoSessionsFromClaimingOneAppliance() {
        CookingSessionStore store = store();
        CookingSession first = session(UUID.randomUUID(), CookingPhase.RESERVED);
        CookingSession second = session(UUID.randomUUID(), CookingPhase.RESERVED);

        assertTrue(store.create(first));
        assertFalse(store.create(second));
        assertEquals(first.sessionId(), store.activeSession(first.appliance()).sessionId());
    }

    @Test
    void terminalTransitionPersistsAndReleasesLock() {
        CookingSessionStore store = store();
        CookingSession session = session(UUID.randomUUID(), CookingPhase.RESERVED);
        assertTrue(store.create(session));

        CookingSession rolledBack = session.transition(CookingPhase.ROLLED_BACK);
        assertTrue(store.update(rolledBack));
        assertFalse(store.locked(session.appliance()));

        CookingSessionStore reloaded = store();
        assertFalse(reloaded.locked(session.appliance()));
        assertEquals(CookingPhase.ROLLED_BACK, reloaded.sessions().getFirst().phase());
    }

    @Test
    void corruptJournalFailsClosedForWrites() throws Exception {
        Files.writeString(temporaryDirectory.resolve("cooking-sessions.yml"), "schema: 99\nsessions: {}\n");

        CookingSessionStore store = store();

        assertFalse(store.writable());
        assertFalse(store.create(session(UUID.randomUUID(), CookingPhase.RESERVED)));
    }

    @Test
    void preservesSlotSnapshotsForRestartReconciliation() {
        CookingSession original = session(UUID.randomUUID(), CookingPhase.RESERVED);
        CookingSession withSnapshots = new CookingSession(
                original.sessionId(), original.villageId(), original.cookUuid(), original.applianceId(),
                original.appliance(), original.recipeId(), original.reserved(), Map.of("cod", 1), Map.of(),
                Map.of(), Map.of(), Map.of(0, "COD:1", 1, "COAL:1", 2, "AIR"),
                original.startedActiveTick(), original.elapsedActiveTicks(), original.requiredActiveTicks(),
                original.phase());
        CookingSessionStore store = store();

        assertTrue(store.create(withSnapshots));
        CookingSession loaded = store().activeSession(original.appliance());

        assertNotNull(loaded);
        assertEquals(withSnapshots.slotSnapshots(), loaded.slotSnapshots());
        assertEquals(withSnapshots.loaded(), loaded.loaded());
    }

    private CookingSessionStore store() {
        return new CookingSessionStore(temporaryDirectory.toFile(), Logger.getLogger("CookingSessionStoreTest"));
    }

    private CookingSession session(UUID id, CookingPhase phase) {
        return new CookingSession(
                id, "stillcliff_1", UUID.randomUUID(), "furnace_1",
                new CookingApplianceKey("StillCliff", 10, 64, 20), "cooked_cod",
                Map.of("cod", 1, "coal", 1), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                100L, 0L, 200L, phase);
    }
}
