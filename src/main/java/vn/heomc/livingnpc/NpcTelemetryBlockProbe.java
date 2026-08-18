package vn.heomc.livingnpc;

import org.bukkit.Material;
import org.bukkit.block.Block;

public record NpcTelemetryBlockProbe(
        String relation,
        String world,
        int x,
        int y,
        int z,
        String material,
        boolean solid,
        boolean passable,
        boolean loadedChunk,
        boolean door,
        boolean fenceGate,
        boolean fence,
        boolean obstacle) {
    static NpcTelemetryBlockProbe from(String relation, Block block, boolean loadedChunk) {
        if (block == null || block.getWorld() == null) return null;
        return classify(
                relation,
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                block.getType(), !block.isPassable(), block.isPassable(), loadedChunk);
    }

    static NpcTelemetryBlockProbe classify(
            String relation, String world, int x, int y, int z, Material material,
            boolean solid, boolean passable, boolean loadedChunk) {
        boolean door = material != null && material.name().endsWith("_DOOR");
        boolean fenceGate = material != null && material.name().endsWith("_FENCE_GATE");
        boolean fence = material != null && material.name().endsWith("_FENCE") && !fenceGate;
        boolean obstacle = solid && !passable && !door && !fenceGate;
        return new NpcTelemetryBlockProbe(
                relation, world, x, y, z, material == null ? "UNKNOWN" : material.name(),
                solid, passable, loadedChunk, door, fenceGate, fence, obstacle);
    }
}
