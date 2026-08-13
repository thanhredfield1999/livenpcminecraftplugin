package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProfessionMonitorTest {
    @Test
    void identifiesTravelWithoutTreatingFishingWaitAsMovement() {
        assertTrue(ProfessionMonitor.isMoving(FarmerPhase.GOING_TO_FISHING_SPOT));
        assertTrue(ProfessionMonitor.isMoving(FarmerPhase.GOING_TO_BED));
        assertTrue(ProfessionMonitor.isMoving(FarmerPhase.GOING_TO_SEAT));
        assertTrue(ProfessionMonitor.isMoving(FarmerPhase.GOING_TO_STALL));
        assertTrue(ProfessionMonitor.isMoving(FarmerPhase.SHELTERING));
        assertFalse(ProfessionMonitor.isMoving(FarmerPhase.LUNCH_BREAK));
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

    @Test
    void treatsConcreteSleepBlockersAsFailures() {
        assertTrue(ProfessionMonitor.sleepFailure("BED_NOT_FOUND_OR_OCCUPIED"));
        assertTrue(ProfessionMonitor.sleepFailure("NO_SAFE_BED_STANDING_BLOCK"));
        assertTrue(ProfessionMonitor.sleepFailure("BED_PATH_UNREACHABLE"));
        assertTrue(ProfessionMonitor.sleepFailure("SLEEP_REJECTED"));
        assertFalse(ProfessionMonitor.sleepFailure("GOING_TO_BED"));
        assertFalse(ProfessionMonitor.sleepFailure("SLEEPING"));
        assertFalse(ProfessionMonitor.sleepFailure("RETRY_COOLDOWN"));
    }
}
