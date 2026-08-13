package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResidentNeedsTest {
    private static final NeedsSettings SETTINGS = new NeedsSettings(true, 100L, 50L, 200L, 1200L);

    @Test
    void decaysFromManagedTicksAndCarriesRemainders() {
        ResidentNeeds needs = new ResidentNeeds(UUID.randomUUID(), "world", 70, 60);

        assertTrue(needs.advance(75L, "world", SETTINGS));
        assertEquals(70, needs.hunger());
        assertEquals(59, needs.thirst());
        assertEquals(75L, needs.hungerDecayTicks());
        assertEquals(25L, needs.thirstDecayTicks());

        needs.advance(25L, "world", SETTINGS);
        assertEquals(69, needs.hunger());
        assertEquals(58, needs.thirst());
        assertEquals(100L, needs.managedTicks());
    }

    @Test
    void capsLongDeltaAndValuesAtZero() {
        ResidentNeeds needs = new ResidentNeeds(UUID.randomUUID(), "world", 1, 1);

        needs.advance(10_000L, "other", SETTINGS);

        assertEquals(0, needs.hunger());
        assertEquals(0, needs.thirst());
        assertEquals(200L, needs.managedTicks());
        assertEquals(0L, needs.hungerDecayTicks());
        assertEquals(0L, needs.thirstDecayTicks());
        assertEquals("other", needs.world());
        assertFalse(needs.advance(0L, "other", SETTINGS));
    }

    @Test
    void clampsLoadedValues() {
        ResidentNeeds needs = new ResidentNeeds(UUID.randomUUID(), "world", 120, -10, -1L, -2L, -3L);

        assertEquals(100, needs.hunger());
        assertEquals(0, needs.thirst());
        assertEquals(0L, needs.managedTicks());
    }
}
