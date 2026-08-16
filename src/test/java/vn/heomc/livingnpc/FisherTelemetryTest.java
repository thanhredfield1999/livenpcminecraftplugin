package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FisherTelemetryTest {
    @Test
    void distinguishesInvalidHookFromLandingTimeout() {
        String invalid = FisherRuntime.hookTelemetryMessage(
                "npc", "HOOK_INVALID", "null", false, false,
                "unavailable", "StillCliff:10,-60,20",
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, 1L);
        String timeout = FisherRuntime.hookTelemetryMessage(
                "npc", "LANDING_TIMEOUT", "FLYING", true, false,
                "StillCliff:12,-59,23", "StillCliff:10,-60,20", 2, 1, 3, 20L);

        assertTrue(invalid.contains("reason=HOOK_INVALID"));
        assertTrue(invalid.contains("hookState=null valid=false inWater=false"));
        assertTrue(invalid.contains("offset=unavailable,unavailable,unavailable ageTicks=1"));
        assertTrue(timeout.contains("reason=LANDING_TIMEOUT"));
        assertTrue(timeout.contains("hookState=FLYING valid=true inWater=false"));
        assertTrue(timeout.contains("offset=2,1,3 ageTicks=20"));
    }
}
