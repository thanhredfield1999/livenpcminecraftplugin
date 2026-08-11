package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkZoneTest {
    @Test
    void containsOnlyPositionsInsideItsWorldAndRanges() {
        WorkZone zone = new WorkZone(
                new StoredLocation("world", 10, 64, 10, 0, 0), 4, 2, TargetMode.AUTO_DISCOVER);

        assertTrue(zone.contains("world", 14, 66, 6));
        assertFalse(zone.contains("world", 15, 64, 10));
        assertFalse(zone.contains("other", 10, 64, 10));
    }

    @Test
    void rejectsNegativeRanges() {
        assertThrows(IllegalArgumentException.class, () -> new WorkZone(
                new StoredLocation("world", 0, 64, 0, 0, 0), -1, 2, TargetMode.MANUAL_TARGET));
    }
}
