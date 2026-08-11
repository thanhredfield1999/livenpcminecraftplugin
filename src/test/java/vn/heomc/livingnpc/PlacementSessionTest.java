package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlacementSessionTest {
    @Test
    void expiresAtConfiguredDeadline() {
        PlacementSession session = new PlacementSession(
                PlacementType.SET_PLOT, java.util.UUID.randomUUID(), null, 4, 1000L);

        assertFalse(session.expired(999L));
        assertTrue(session.expired(1000L));
    }
}
