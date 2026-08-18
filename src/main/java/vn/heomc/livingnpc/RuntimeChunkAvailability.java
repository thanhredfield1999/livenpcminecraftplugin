package vn.heomc.livingnpc;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;

final class RuntimeChunkAvailability {
    private RuntimeChunkAvailability() {
    }

    static boolean loaded(Location location) {
        if (location == null) return false;
        World world = location.getWorld();
        return world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    static boolean loadedArea(Location center, int radius) {
        if (center == null || center.getWorld() == null) return false;
        World world = center.getWorld();
        int boundedRadius = Math.max(0, radius);
        int minChunkX = (center.getBlockX() - boundedRadius) >> 4;
        int maxChunkX = (center.getBlockX() + boundedRadius) >> 4;
        int minChunkZ = (center.getBlockZ() - boundedRadius) >> 4;
        int maxChunkZ = (center.getBlockZ() + boundedRadius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    static boolean loadedRoute(List<Location> route) {
        if (route == null || route.isEmpty()) return false;
        Location first = route.get(0);
        if (first == null || first.getWorld() == null) return false;
        for (Location waypoint : route) {
            if (waypoint == null || !first.getWorld().equals(waypoint.getWorld())
                    || !loaded(waypoint)) return false;
        }
        return true;
    }
}
