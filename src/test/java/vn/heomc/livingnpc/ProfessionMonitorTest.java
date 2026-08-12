package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProfessionMonitorTest {
    @Test
    void identifiesTravelWithoutTreatingFishingWaitAsMovement() {
        assertTrue(ProfessionMonitor.isMoving(FarmerPhase.GOING_TO_FISHING_SPOT));
        assertTrue(ProfessionMonitor.isMoving(FarmerPhase.GOING_TO_SEAT));
        assertTrue(ProfessionMonitor.isMoving(FarmerPhase.GOING_TO_STALL));
        assertFalse(ProfessionMonitor.isMoving(FarmerPhase.WANDERING));
        assertFalse(ProfessionMonitor.isMoving(FarmerPhase.CASTING_LINE));
        assertFalse(ProfessionMonitor.isMoving(FarmerPhase.WAITING_FOR_BITE));
        assertFalse(ProfessionMonitor.isMoving(FarmerPhase.REELING_IN));
    }

    @Test
    void allowsOneSecondForCitizensNavigationToStart() {
        assertFalse(ProfessionMonitor.navigationGraceExpired(100L, 119L));
        assertTrue(ProfessionMonitor.navigationGraceExpired(100L, 120L));
    }
}
