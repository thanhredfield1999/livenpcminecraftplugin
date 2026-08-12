package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MinerSafetyTest {
    @Test
    void recognizesLivingAndWorkFeatures() {
        assertTrue(CivilProfessionRuntime.isProtectedFeature(Material.RED_BED));
        assertTrue(CivilProfessionRuntime.isProtectedFeature(Material.CHEST));
        assertTrue(CivilProfessionRuntime.isProtectedFeature(Material.CRAFTING_TABLE));
        assertTrue(CivilProfessionRuntime.isProtectedFeature(Material.BLAST_FURNACE));
        assertTrue(CivilProfessionRuntime.isProtectedFeature(Material.DAMAGED_ANVIL));
    }

    @Test
    void allowsOrdinaryCaveBlocks() {
        assertFalse(CivilProfessionRuntime.isProtectedFeature(Material.STONE));
        assertFalse(CivilProfessionRuntime.isProtectedFeature(Material.DEEPSLATE));
        assertFalse(CivilProfessionRuntime.isProtectedFeature(Material.COAL_ORE));
        assertFalse(CivilProfessionRuntime.isProtectedFeature(Material.TORCH));
    }

    @Test
    void recognizesDedicatedWorldGuardMiningRegionIds() {
        assertTrue(WorldMutationPolicy.isMiningRegionId("lnpc-mine-stillcliff"));
        assertTrue(WorldMutationPolicy.isMiningRegionId("LNPC-MINE-NORTH"));
        assertFalse(WorldMutationPolicy.isMiningRegionId("safezone"));
        assertFalse(WorldMutationPolicy.isMiningRegionId("lnpc-mineshaft"));
    }
}
