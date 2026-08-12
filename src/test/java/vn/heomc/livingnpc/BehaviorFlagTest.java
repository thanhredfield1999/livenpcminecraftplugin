package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertFalse(defaults.contains(BehaviorFlag.CHARACTER_PROFILE));
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

    @Test
    void farmerToggleControlsAllRequiredWorkFlagsTogether() {
        FarmerDefinition original = new FarmerDefinition(
                java.util.UUID.randomUUID(),
                new StoredLocation("world", 0, 64, 0, 0, 0),
                null,
                4,
                ResidentProfile.custom("Worker"),
                BehaviorFlag.safeDefaults());

        FarmerDefinition enabled = original.withFarmerEnabled(true);
        FarmerDefinition disabled = enabled.withFarmerEnabled(false);

        assertTrue(enabled.enabled(BehaviorFlag.MASTER));
        assertTrue(enabled.enabled(BehaviorFlag.HARVEST));
        assertTrue(enabled.enabled(BehaviorFlag.PLANT));
        assertFalse(disabled.enabled(BehaviorFlag.MASTER));
        assertFalse(disabled.enabled(BehaviorFlag.HARVEST));
        assertFalse(disabled.enabled(BehaviorFlag.PLANT));
    }

    @Test
    void lookPitchIsHorizontalForLevelTargetsAndBoundedForBlocks() {
        assertEquals(0.0f, FarmerRuntime.lookPitchDegrees(4.0, 0.0), 0.001f);
        assertTrue(FarmerRuntime.lookPitchDegrees(1.0, -2.0) > 0.0f);
        assertEquals(60.0f, FarmerRuntime.lookPitchDegrees(0.1, -100.0), 0.001f);
    }
}
