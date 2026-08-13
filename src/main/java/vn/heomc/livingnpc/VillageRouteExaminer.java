package vn.heomc.livingnpc;

import net.citizensnpcs.api.astar.pathfinder.BlockExaminer;
import net.citizensnpcs.api.astar.pathfinder.BlockSource;
import net.citizensnpcs.api.astar.pathfinder.PathPoint;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.Vector;

final class VillageRouteExaminer implements BlockExaminer {
    @Override
    public float getCost(BlockSource source, PathPoint point) {
        Vector position = point.getVector();
        int x = position.getBlockX();
        int y = position.getBlockY();
        int z = position.getBlockZ();
        Material support = source.getMaterialAt(x, y - 1, z);
        float cost = support == Material.DIRT_PATH ? 0.0F : 2.0F;

        for (int[] offset : CARDINAL_OFFSETS) {
            int adjacentX = x + offset[0];
            int adjacentZ = z + offset[1];
            Material feet = source.getMaterialAt(adjacentX, y, adjacentZ);
            BlockData feetData = source.getBlockDataAt(adjacentX, y, adjacentZ);
            if (isLiquid(feet, feetData)) {
                cost += 8.0F;
                continue;
            }
            Material adjacentSupport = source.getMaterialAt(adjacentX, y - 1, adjacentZ);
            BlockData supportData = source.getBlockDataAt(adjacentX, y - 1, adjacentZ);
            if (isAir(feet)
                    && !canSupportRoute(adjacentSupport, supportData)) {
                cost += 5.0F;
            }
        }
        return cost;
    }

    @Override
    public PassableState isPassable(BlockSource source, PathPoint point) {
        return PassableState.IGNORE;
    }

    private static boolean isLiquid(Material material, BlockData data) {
        return material == Material.WATER || material == Material.LAVA
                || data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    private static boolean canSupportRoute(Material material, BlockData data) {
        return !isAir(material) && !isLiquid(material, data);
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private static final int[][] CARDINAL_OFFSETS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
}
