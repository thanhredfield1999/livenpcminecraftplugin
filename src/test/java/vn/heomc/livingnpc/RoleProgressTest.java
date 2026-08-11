package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoleProgressTest {
    @Test
    void startsAtLevelOneAndCapsAtOneHundred() {
        assertEquals(1, new RoleProgress(0).level());
        assertEquals(2, new RoleProgress(25).level());
        assertEquals(100, new RoleProgress(Long.MAX_VALUE).level());
    }

    @Test
    void speedBonusNeverExceedsTwentyPercent() {
        assertEquals(1.0, new RoleProgress(0).speedMultiplier());
        assertTrue(new RoleProgress(Long.MAX_VALUE).speedMultiplier() <= 1.2);
    }
}
