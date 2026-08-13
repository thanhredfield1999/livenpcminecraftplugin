package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

class ReleasePolicyTest {
    @Test
    void seasonTwoEnablesOnlyDailyLifeFishingAndRanching() {
        assertTrue(ReleasePolicy.roleEnabled(ResidentRole.RESIDENT));
        assertTrue(ReleasePolicy.roleEnabled(ResidentRole.FARMER));
        assertTrue(ReleasePolicy.roleEnabled(ResidentRole.FISHER));
        assertTrue(ReleasePolicy.roleEnabled(ResidentRole.RANCHER));
        assertFalse(ReleasePolicy.roleEnabled(ResidentRole.MERCHANT));
        assertFalse(ReleasePolicy.roleEnabled(ResidentRole.COOK));
        assertFalse(ReleasePolicy.roleEnabled(ResidentRole.CRAFTER));
        assertFalse(ReleasePolicy.roleEnabled(ResidentRole.MINER));
        assertFalse(ReleasePolicy.roleEnabled(ResidentRole.SECURITY));
        assertTrue(ReleasePolicy.seasonTwoRuntimesEnabled());
        assertFalse(ReleasePolicy.seasonThreeRuntimesEnabled());
        assertFalse(ReleasePolicy.seasonFourRuntimesEnabled());
        assertFalse(ReleasePolicy.seasonNineRuntimesEnabled());
    }

    @Test
    void enabledRolesExposeExactlyTheSeasonTwoJobs() {
        assertTrue(ReleasePolicy.enabledRoles().equals(EnumSet.of(
                ResidentRole.RESIDENT, ResidentRole.FARMER, ResidentRole.FISHER, ResidentRole.RANCHER)));
    }

    @Test
    void seasonTwoExposesOnlyFishingAndRanchInfrastructure() {
        assertTrue(ReleasePolicy.workZoneEnabled(VillageWorkZoneType.FISHING));
        assertTrue(ReleasePolicy.workZoneEnabled(VillageWorkZoneType.RANCH));
        assertFalse(ReleasePolicy.workZoneEnabled(VillageWorkZoneType.WOOD));
        assertFalse(ReleasePolicy.workZoneEnabled(VillageWorkZoneType.COOKING));
        assertFalse(ReleasePolicy.workZoneEnabled(VillageWorkZoneType.CRAFTING));
        assertFalse(ReleasePolicy.workZoneEnabled(VillageWorkZoneType.MINING));
        assertFalse(ReleasePolicy.workZoneEnabled(VillageWorkZoneType.SECURITY));
    }
}
