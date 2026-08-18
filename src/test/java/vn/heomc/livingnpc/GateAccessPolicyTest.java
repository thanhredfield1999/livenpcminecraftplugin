package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GateAccessPolicyTest {
    @Test
    void residentCannotOpenProfessionFenceGate() {
        assertFalse(GateAccessPolicy.mayOpenFenceGate(ResidentRole.RESIDENT, "RANCHER"));
        assertFalse(GateAccessPolicy.mayOpenFenceGate(ResidentRole.RESIDENT, "FARMER"));
    }

    @Test
    void rancherCanOpenOnlyRancherGate() {
        assertTrue(GateAccessPolicy.mayOpenFenceGate(ResidentRole.RANCHER, "RANCHER"));
        assertFalse(GateAccessPolicy.mayOpenFenceGate(ResidentRole.RANCHER, "FARMER"));
    }

    @Test
    void missingAccessClassFailsClosed() {
        assertFalse(GateAccessPolicy.mayOpenFenceGate(ResidentRole.FARMER, null));
        assertFalse(GateAccessPolicy.mayOpenFenceGate(ResidentRole.FARMER, "unknown"));
    }

    @Test
    void navigationGateRoundTripKeepsAccessClass() {
        NavigationGate gate = new NavigationGate(
                new StoredLocation("world", 1, 64, 2, 0, 0), "rancher");
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        gate.save(yaml.createSection("gate"));
        NavigationGate loaded = NavigationGate.load(yaml.getConfigurationSection("gate"));
        assertEquals("RANCHER", loaded.accessClass());
        assertEquals(gate.location(), loaded.location());
    }
}
