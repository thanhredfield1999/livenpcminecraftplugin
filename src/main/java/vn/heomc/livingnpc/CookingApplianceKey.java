package vn.heomc.livingnpc;

import org.bukkit.Location;
import org.bukkit.block.Block;

record CookingApplianceKey(String world, int x, int y, int z) {
    CookingApplianceKey {
        if (world == null || world.isBlank()) throw new IllegalArgumentException("Appliance world is required");
    }

    static CookingApplianceKey from(Block block) {
        return new CookingApplianceKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    static CookingApplianceKey from(Location location) {
        return new CookingApplianceKey(
                location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    String storageKey() {
        return world + ";" + x + ";" + y + ";" + z;
    }
}
