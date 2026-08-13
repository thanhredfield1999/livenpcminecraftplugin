package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class MiningRestorationStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void pendingRestorationCanRunWithoutMinerRuntime() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("StillCliff");
        when(world.isChunkLoaded(0, 1)).thenReturn(true);
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(10);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(20);
        when(block.getLocation()).thenReturn(new Location(world, 10, 64, 20));
        when(block.getType()).thenReturn(Material.COBBLESTONE);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(block);
        BlockData original = mock(BlockData.class);
        when(original.getAsString()).thenReturn("minecraft:iron_ore");
        BlockData restored = mock(BlockData.class);

        MiningRestorationStore store = new MiningRestorationStore(
                temporaryDirectory.toFile(), Logger.getLogger("MiningRestorationStoreTest"));
        assertTrue(store.record(block, original, Material.COBBLESTONE, 0L));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("StillCliff")).thenReturn(world);
            bukkit.when(() -> Bukkit.createBlockData("minecraft:iron_ore")).thenReturn(restored);

            new MiningRestorationStore(
                    temporaryDirectory.toFile(), Logger.getLogger("MiningRestorationStoreTest"))
                    .tick(1L, 8);
        }

        verify(block).setBlockData(restored, false);
    }
}
