package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NpcBehaviorPolicyTest {
    @Test
    void socialRuntimeOnlyOwnsResidentsAndFarmers() {
        assertTrue(FarmerManager.socialRoleEligible(ResidentRole.RESIDENT));
        assertTrue(FarmerManager.socialRoleEligible(ResidentRole.FARMER));
        assertFalse(FarmerManager.socialRoleEligible(ResidentRole.FISHER));
        assertFalse(FarmerManager.socialRoleEligible(ResidentRole.RANCHER));
        assertFalse(FarmerManager.socialRoleEligible(ResidentRole.MERCHANT));
    }

    @Test
    void fisherLootMatchesConfiguredDistributionBoundaries() {
        assertEquals("cod", FisherRuntime.fishForRoll(0.00));
        assertEquals("cod", FisherRuntime.fishForRoll(0.5999));
        assertEquals("salmon", FisherRuntime.fishForRoll(0.60));
        assertEquals("pufferfish", FisherRuntime.fishForRoll(0.85));
        assertEquals("tropical_fish", FisherRuntime.fishForRoll(0.98));
    }
}
