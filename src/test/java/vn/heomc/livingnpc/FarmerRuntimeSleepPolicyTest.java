package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FarmerRuntimeSleepPolicyTest {
    @Test
    void sleepsOnlyDuringConfiguredNightWindow() {
        assertFalse(FarmerRuntime.isBedtime(12999L));
        assertTrue(FarmerRuntime.isBedtime(13000L));
        assertTrue(FarmerRuntime.isBedtime(22999L));
        assertFalse(FarmerRuntime.isBedtime(23000L));
        assertFalse(FarmerRuntime.isBedtime(0L));
    }
}
