package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FisherRuntimeEquipmentTest {
    @Test
    void requiresRodThroughoutEveryFishingPhase() {
        assertTrue(FisherRuntime.requiresFishingRod(FarmerPhase.CASTING_LINE));
        assertTrue(FisherRuntime.requiresFishingRod(FarmerPhase.WAITING_FOR_BITE));
        assertTrue(FisherRuntime.requiresFishingRod(FarmerPhase.REELING_IN));
        assertFalse(FisherRuntime.requiresFishingRod(FarmerPhase.GOING_TO_FISHING_SPOT));
        assertFalse(FisherRuntime.requiresFishingRod(FarmerPhase.RESTING));
        assertFalse(FisherRuntime.requiresFishingRod(FarmerPhase.INACTIVE));
    }
}
