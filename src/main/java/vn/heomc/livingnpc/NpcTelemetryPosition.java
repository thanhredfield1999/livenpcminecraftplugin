package vn.heomc.livingnpc;

import org.bukkit.Location;

public record NpcTelemetryPosition(
        String world,
        int blockX,
        int blockY,
        int blockZ,
        double x,
        double y,
        double z,
        float yaw,
        float pitch) {
    static NpcTelemetryPosition from(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return new NpcTelemetryPosition(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }
}
