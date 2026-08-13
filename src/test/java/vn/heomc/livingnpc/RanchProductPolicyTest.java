package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class RanchProductPolicyTest {
    @Test
    void collectsOnlyGroundProductsFromRanching() {
        assertTrue(RancherRuntime.ranchProduct(Material.EGG));
        assertTrue(RancherRuntime.ranchProduct(Material.WHITE_WOOL));
        assertTrue(RancherRuntime.ranchProduct(Material.BLACK_WOOL));
        assertFalse(RancherRuntime.ranchProduct(Material.WHEAT));
        assertFalse(RancherRuntime.ranchProduct(Material.DIAMOND));
    }
}
