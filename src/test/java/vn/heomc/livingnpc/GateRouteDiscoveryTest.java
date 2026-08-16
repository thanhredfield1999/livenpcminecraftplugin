package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Gate;
import org.junit.jupiter.api.Test;

class GateRouteDiscoveryTest {
    @Test
    void doesNotReadConfiguredGateFromUnloadedChunk() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

        List<GateRoute.Candidate> candidates = GateRouteDiscovery.discover(
                new Location(world, 0.5, 64.0, 0.5),
                new Location(world, 20.5, 64.0, 0.5),
                List.of(new StoredLocation("world", 5, 64, 0, 0, 0)));

        assertTrue(candidates.isEmpty());
        verify(world).isChunkLoaded(0, 0);
    }

    @Test
    void usesOnlyConfiguredGateBlocksAndSortsCandidatesByTotalDetour() {
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getName()).thenReturn("world");
        Block empty = block(Material.AIR, null);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(empty);
        addGate(world, 5, 64, 0, BlockFace.EAST);
        addGate(world, 15, 64, 4, BlockFace.EAST);

        List<GateRoute.Candidate> candidates = GateRouteDiscovery.discover(
                new Location(world, 0.5, 64.0, 0.5),
                new Location(world, 20.5, 64.0, 0.5),
                List.of(
                        new StoredLocation("world", 15, 64, 4, 0, 0),
                        new StoredLocation("world", 5, 64, 0, 0, 0)));

        assertEquals(List.of("world:5:64:0", "world:15:64:4"),
                candidates.stream().map(GateRoute.Candidate::key).toList());
    }

    @Test
    void rejectsConfiguredPointWhenBlockIsNoLongerAFenceGate() {
        World world = mock(World.class);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getName()).thenReturn("world");
        Block empty = block(Material.AIR, null);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(empty);

        List<GateRoute.Candidate> candidates = GateRouteDiscovery.discover(
                new Location(world, 0.5, 64.0, 0.5),
                new Location(world, 20.5, 64.0, 0.5),
                List.of(new StoredLocation("world", 5, 64, 0, 0, 0)));

        assertTrue(candidates.isEmpty());
    }

    @Test
    void doesNotRouteThroughGateWhenGoalIsItsApproachStandingBlock() {
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getName()).thenReturn("world");
        Block empty = block(Material.AIR, null);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(empty);
        addGate(world, 5, 64, 0, BlockFace.EAST);

        List<GateRoute.Candidate> candidates = GateRouteDiscovery.discover(
                new Location(world, 0.5, 64.0, 0.5),
                new Location(world, 4.5, 64.0, 0.5),
                List.of(new StoredLocation("world", 5, 64, 0, 0, 0)));

        assertTrue(candidates.isEmpty());
    }

    @Test
    void rejectsGateWhoseExitIncreasesDistanceToGoal() {
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getName()).thenReturn("world");
        Block empty = block(Material.AIR, null);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(empty);
        addGate(world, 49, 64, -17, BlockFace.SOUTH);

        List<GateRoute.Candidate> candidates = GateRouteDiscovery.discover(
                new Location(world, 48.5062, -60.0625, -18.4081),
                new Location(world, 68.0, -60.0, -21.0),
                List.of(new StoredLocation("world", 49, 64, -17, 0, 0)));

        assertTrue(candidates.isEmpty());
    }

    @Test
    void usesOnlyGateStrictlyBetweenCurrentAndGoal() {
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getName()).thenReturn("world");
        Block empty = block(Material.AIR, null);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(empty);
        addGate(world, -2, 64, 0, BlockFace.EAST);
        addGate(world, 10, 64, 0, BlockFace.EAST);
        addGate(world, 22, 64, 0, BlockFace.EAST);

        List<GateRoute.Candidate> candidates = GateRouteDiscovery.discover(
                new Location(world, 0.5, 64.0, 0.5),
                new Location(world, 20.5, 64.0, 0.5),
                List.of(
                        new StoredLocation("world", -2, 64, 0, 0, 0),
                        new StoredLocation("world", 10, 64, 0, 0, 0),
                        new StoredLocation("world", 22, 64, 0, 0, 0)));

        assertEquals(List.of("world:10:64:0"),
                candidates.stream().map(GateRoute.Candidate::key).toList());
    }

    private static void addGate(World world, int x, int y, int z, BlockFace facing) {
        Gate gateData = mock(Gate.class);
        when(gateData.getFacing()).thenReturn(facing);
        Block gate = block(Material.OAK_FENCE_GATE, gateData);
        when(world.getBlockAt(x, y, z)).thenReturn(gate);
        standing(world, x - facing.getModX(), y, z - facing.getModZ());
        standing(world, x + facing.getModX(), y, z + facing.getModZ());
    }

    private static void standing(World world, int x, int y, int z) {
        Block feet = block(Material.AIR, null);
        Block head = block(Material.AIR, null);
        Block floor = block(Material.STONE, null);
        when(world.getBlockAt(x, y, z)).thenReturn(feet);
        when(world.getBlockAt(x, y + 1, z)).thenReturn(head);
        when(world.getBlockAt(x, y - 1, z)).thenReturn(floor);
    }

    private static Block block(Material material, org.bukkit.block.data.BlockData data) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.isPassable()).thenReturn(material == Material.AIR);
        when(block.getBlockData()).thenReturn(data);
        return block;
    }
}
