package vn.heomc.livingnpc;

import java.util.ArrayList;
import java.util.List;
import net.citizensnpcs.api.astar.pathfinder.BlockExaminer;
import net.citizensnpcs.api.astar.pathfinder.BlockSource;
import net.citizensnpcs.api.astar.pathfinder.PathPoint;
import net.citizensnpcs.api.astar.pathfinder.VectorNode;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.Vector;

final class VillageRouteExaminer implements BlockExaminer.ReplacementNeighbourGenerator {
    @Override
    public List<PathPoint> getNeighbours(BlockSource source, PathPoint point) {
        if (!(point instanceof VectorNode node)) return null;
        List<PathPoint> neighbours = new ArrayList<>(node.getNeighbours(source, point, true));
        neighbours.removeIf(candidate -> isUnsupportedEdge(source, candidate));
        return neighbours;
    }

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
            if (isUnsupportedEdge(source, adjacentX, y, adjacentZ, feet)) {
                cost += 5.0F;
            }
        }
        return cost;
    }

    @Override
    public PassableState isPassable(BlockSource source, PathPoint point) {
        return PassableState.IGNORE;
    }

    static boolean isUnsupportedEdge(BlockSource source, PathPoint point) {
        Vector position = point.getVector();
        int x = position.getBlockX();
        int y = position.getBlockY();
        int z = position.getBlockZ();
        Material feet = source.getMaterialAt(x, y, z);
        return isUnsupportedEdge(source, x, y, z, feet);
    }

    private static boolean isUnsupportedEdge(
            BlockSource source, int adjacentX, int y, int adjacentZ, Material feet) {
        if (!isAir(feet)) return false;
        Material support = source.getMaterialAt(adjacentX, y - 1, adjacentZ);
        BlockData supportData = source.getBlockDataAt(adjacentX, y - 1, adjacentZ);
        return !canSupportRoute(support, supportData);
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
