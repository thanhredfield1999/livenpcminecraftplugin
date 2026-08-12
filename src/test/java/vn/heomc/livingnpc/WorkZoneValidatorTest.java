package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class WorkZoneValidatorTest {
    @Test
    void validatesEachWorkZoneRequirement() {
        assertTrue(WorkZoneValidator.evaluate(
                VillageWorkZoneType.WOOD,
                Set.of(Material.STONECUTTER, Material.CRAFTING_TABLE)).valid());
        assertTrue(WorkZoneValidator.evaluate(
                VillageWorkZoneType.COOKING,
                Set.of(Material.FURNACE, Material.CRAFTING_TABLE)).valid());
        assertTrue(WorkZoneValidator.evaluate(
                VillageWorkZoneType.CRAFTING,
                Set.of(Material.CRAFTING_TABLE, Material.SMITHING_TABLE, Material.CHIPPED_ANVIL)).valid());
        assertTrue(WorkZoneValidator.evaluate(
                VillageWorkZoneType.RANCH,
                Set.of(Material.HAY_BLOCK, Material.SPRUCE_FENCE_GATE)).valid());
        assertTrue(WorkZoneValidator.evaluate(
                VillageWorkZoneType.FISHING,
                Set.of(Material.WATER)).valid());
        assertTrue(WorkZoneValidator.evaluate(
                VillageWorkZoneType.MINING,
                Set.of(Material.STONECUTTER, Material.BLAST_FURNACE)).valid());
        assertTrue(WorkZoneValidator.evaluate(
                VillageWorkZoneType.SECURITY,
                Set.of(Material.BELL, Material.TARGET)).valid());
    }

    @Test
    void reportsMissingStations() {
        WorkZoneValidation validation = WorkZoneValidator.evaluate(
                VillageWorkZoneType.CRAFTING, Set.of(Material.CRAFTING_TABLE));

        assertFalse(validation.valid());
        assertTrue(validation.missing().contains(Material.SMITHING_TABLE));
        assertTrue(validation.missing().contains(Material.ANVIL));
    }
}
