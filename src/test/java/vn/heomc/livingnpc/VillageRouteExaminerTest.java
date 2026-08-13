package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.citizensnpcs.api.astar.pathfinder.BlockSource;
import net.citizensnpcs.api.astar.pathfinder.PathPoint;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class VillageRouteExaminerTest {
    private final VillageRouteExaminer examiner = new VillageRouteExaminer();

    @Test
    void prefersDirtPathOverOrdinaryGround() {
        BlockSource road = flatSource(Material.DIRT_PATH);
        BlockSource grass = flatSource(Material.GRASS_BLOCK);
        PathPoint point = pointAt(0, 1, 0);

        assertTrue(examiner.getCost(road, point) < examiner.getCost(grass, point));
    }

    @Test
    void stronglyPenalizesRoutesBesideWater() {
        BlockSource safe = flatSource(Material.DIRT_PATH);
        BlockSource shoreline = flatSource(Material.DIRT_PATH);
        when(shoreline.getMaterialAt(1, 1, 0)).thenReturn(Material.WATER);

        float safeCost = examiner.getCost(safe, pointAt(0, 1, 0));
        float shorelineCost = examiner.getCost(shoreline, pointAt(0, 1, 0));

        assertTrue(shorelineCost >= safeCost + 8.0F);
    }

    @Test
    void stronglyPenalizesRoutesBesideUnsupportedEdges() {
        BlockSource edge = flatSource(Material.DIRT_PATH);
        when(edge.getMaterialAt(1, 0, 0)).thenReturn(Material.AIR);

        assertEquals(5.0F, examiner.getCost(edge, pointAt(0, 1, 0)));
    }

    private BlockSource flatSource(Material support) {
        BlockSource source = mock(BlockSource.class);
        BlockData data = mock(BlockData.class);
        when(source.getMaterialAt(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 0 ? support : Material.AIR);
        when(source.getBlockDataAt(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(data);
        return source;
    }

    private PathPoint pointAt(int x, int y, int z) {
        PathPoint point = mock(PathPoint.class);
        when(point.getVector()).thenReturn(new Vector(x, y, z));
        return point;
    }
}
