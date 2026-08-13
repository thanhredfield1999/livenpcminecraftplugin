package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CookingSessionTest {
    @Test
    void permitsOnlyForwardTransitionsOrRollback() {
        CookingSession reserved = session(CookingPhase.RESERVED);

        CookingSession loaded = reserved.transition(CookingPhase.LOADED);
        CookingSession rolledBack = loaded.transition(CookingPhase.ROLLED_BACK);

        assertEquals(CookingPhase.LOADED, loaded.phase());
        assertFalse(rolledBack.active());
        assertThrows(IllegalStateException.class, () -> reserved.transition(CookingPhase.COOKING));
        assertThrows(IllegalStateException.class, () -> rolledBack.transition(CookingPhase.COMMITTED));
    }

    @Test
    void rejectsInvalidAccountingData() {
        assertThrows(IllegalArgumentException.class, () -> new CookingSession(
                UUID.randomUUID(), "village", UUID.randomUUID(), "furnace_1",
                new CookingApplianceKey("world", 1, 2, 3), "cod",
                Map.of("cod", -1), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                0L, 0L, 200L, CookingPhase.RESERVED));
    }

    @Test
    void terminalPhasesAreNotActive() {
        assertTrue(CookingPhase.COOKED.active());
        assertFalse(CookingPhase.COMMITTED.active());
        assertFalse(CookingPhase.ROLLED_BACK.active());
    }

    static CookingSession session(CookingPhase phase) {
        return new CookingSession(
                UUID.randomUUID(), "stillcliff_1", UUID.randomUUID(), "furnace_1",
                new CookingApplianceKey("StillCliff", 10, 64, 20), "cooked_cod",
                Map.of("cod", 1, "coal", 1), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                100L, 0L, 200L, phase);
    }
}
