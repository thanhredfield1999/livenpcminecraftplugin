package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class BehaviorFlagTest {
    @Test
    void safeDefaultsNeverChangeBlocks() {
        EnumSet<BehaviorFlag> defaults = BehaviorFlag.safeDefaults();

        assertTrue(defaults.contains(BehaviorFlag.MASTER));
        assertFalse(defaults.contains(BehaviorFlag.HARVEST));
        assertFalse(defaults.contains(BehaviorFlag.PLANT));
        assertFalse(defaults.contains(BehaviorFlag.SELL_INVENTORY));
    }

    @Test
    void togglesAreImmutableFromPreviousDefinition() {
        FarmerDefinition original = new FarmerDefinition(
                java.util.UUID.randomUUID(),
                new StoredLocation("world", 0, 64, 0, 0, 0),
                null,
                4,
                ResidentProfile.custom("Aldric"),
                BehaviorFlag.safeDefaults());

        FarmerDefinition changed = original.withBehavior(BehaviorFlag.HARVEST, true);

        assertFalse(original.enabled(BehaviorFlag.HARVEST));
        assertTrue(changed.enabled(BehaviorFlag.HARVEST));
    }
}
