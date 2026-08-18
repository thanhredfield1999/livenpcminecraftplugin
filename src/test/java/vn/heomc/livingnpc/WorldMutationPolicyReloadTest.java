package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

/**
 * R-004: WorldMutationPolicy snapshot không thay đổi sau reload.
 * Khi config hoặc WorldGuard availability thay đổi so với enable-time,
 * reload phải báo restart-required, không hot-replace policy.
 */
class WorldMutationPolicyReloadTest {

    @Test
    void policySnapshotsAreImmutableAfterConstruction() {
        PluginManager pm = mock(PluginManager.class);
        when(pm.isPluginEnabled("WorldGuard")).thenReturn(true);

        WorldMutationPolicy policy = new WorldMutationPolicy(pm, true);

        assertTrue(policy.available(), "snapshot WorldGuard available");
        assertTrue(policy.requiresWorldGuard(), "snapshot requireWorldGuard");

        // Giả lập WorldGuard bị gỡ sau enable — policy snapshot không đổi
        when(pm.isPluginEnabled("WorldGuard")).thenReturn(false);
        assertTrue(policy.available(), "snapshot immutable after environment change");
        assertTrue(policy.requiresWorldGuard(), "requiresWorldGuard immutable");
    }

    @Test
    void detectsRequireWorldGuardConfigDrift() {
        PluginManager pm = mock(PluginManager.class);
        when(pm.isPluginEnabled("WorldGuard")).thenReturn(true);

        // Enable với requireWorldGuard=true
        WorldMutationPolicy policy = new WorldMutationPolicy(pm, true);

        // Sau reload, config đổi thành false — phải detect drift
        boolean newRequireWorldGuard = false;
        assertTrue(policy.requiresWorldGuard() != newRequireWorldGuard,
                "config drift detected: protection.require-worldguard");
    }

    @Test
    void detectsWorldGuardAvailabilityDrift() {
        PluginManager pm = mock(PluginManager.class);
        when(pm.isPluginEnabled("WorldGuard")).thenReturn(false);

        // Enable khi WorldGuard chưa có
        WorldMutationPolicy policy = new WorldMutationPolicy(pm, false);
        assertFalse(policy.available());

        // Sau enable, WorldGuard được cài — phải detect drift
        boolean currentAvailability = true;
        assertTrue(policy.available() != currentAvailability,
                "availability drift detected");
    }

    @Test
    void noRestartRequiredWhenNothingChanged() {
        PluginManager pm = mock(PluginManager.class);
        when(pm.isPluginEnabled("WorldGuard")).thenReturn(true);

        WorldMutationPolicy policy = new WorldMutationPolicy(pm, true);

        // Simulate reload detection — same values
        List<String> restartRequired = policy.restartRequiredReasons(pm, true);
        assertTrue(restartRequired.isEmpty(), "no drift, no restart needed");
    }

    @Test
    void reportsRequireWorldGuardChange() {
        PluginManager pm = mock(PluginManager.class);
        when(pm.isPluginEnabled("WorldGuard")).thenReturn(true);

        WorldMutationPolicy policy = new WorldMutationPolicy(pm, true);

        // Config changed to false
        List<String> restartRequired = policy.restartRequiredReasons(pm, false);
        assertEquals(1, restartRequired.size());
        assertEquals("protection.require-worldguard", restartRequired.get(0));
    }

    @Test
    void reportsWorldGuardAvailabilityChange() {
        PluginManager pm = mock(PluginManager.class);
        when(pm.isPluginEnabled("WorldGuard")).thenReturn(true);

        WorldMutationPolicy policy = new WorldMutationPolicy(pm, false);

        // WorldGuard removed at runtime
        when(pm.isPluginEnabled("WorldGuard")).thenReturn(false);
        List<String> restartRequired = policy.restartRequiredReasons(pm, false);
        assertEquals(1, restartRequired.size());
        assertEquals("WorldGuard availability", restartRequired.get(0));
    }

    @Test
    void reportsBothChanges() {
        PluginManager pm = mock(PluginManager.class);
        when(pm.isPluginEnabled("WorldGuard")).thenReturn(true);

        WorldMutationPolicy policy = new WorldMutationPolicy(pm, true);

        // Both config and availability changed
        when(pm.isPluginEnabled("WorldGuard")).thenReturn(false);
        List<String> restartRequired = policy.restartRequiredReasons(pm, false);
        assertEquals(2, restartRequired.size());
        assertTrue(restartRequired.contains("protection.require-worldguard"));
        assertTrue(restartRequired.contains("WorldGuard availability"));
    }

}
