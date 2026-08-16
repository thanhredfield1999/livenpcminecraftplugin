package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
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

    @Test
    void corruptJournalRejectsNewMiningBeforeBlockMutation() throws Exception {
        Files.writeString(temporaryDirectory.resolve("mining-restorations.yml"), "blocks: [broken\n");
        Block block = miningBlock(Material.IRON_ORE);
        BlockData original = mock(BlockData.class);
        when(original.getAsString()).thenReturn("minecraft:iron_ore");

        MiningRestorationStore store = new MiningRestorationStore(
                temporaryDirectory.toFile(), Logger.getLogger("MiningRestorationStoreTest"));

        assertFalse(store.record(block, original, Material.COBBLESTONE, 0L));
        verify(block, never()).setType(org.mockito.ArgumentMatchers.any(Material.class),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void playerChangedBlockIsNotOverwrittenAndJournalEntryIsCleared() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("StillCliff");
        when(world.isChunkLoaded(0, 1)).thenReturn(true);
        Block block = miningBlock(world, Material.DIAMOND_BLOCK);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(block);
        BlockData original = mock(BlockData.class);
        when(original.getAsString()).thenReturn("minecraft:iron_ore");

        MiningRestorationStore store = new MiningRestorationStore(
                temporaryDirectory.toFile(), Logger.getLogger("MiningRestorationStoreTest"));
        assertTrue(store.record(block, original, Material.COBBLESTONE, 0L));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("StillCliff")).thenReturn(world);
            store.tick(1L, 8);
            new MiningRestorationStore(
                    temporaryDirectory.toFile(), Logger.getLogger("MiningRestorationStoreTest"))
                    .tick(2L, 8);
        }

        verify(block, never()).setBlockData(org.mockito.ArgumentMatchers.any(BlockData.class),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void failedJournalSaveKeepsRestorationPendingInMemory() throws Exception {
        World world = mock(World.class);
        when(world.getName()).thenReturn("StillCliff");
        when(world.isChunkLoaded(0, 1)).thenReturn(true);
        Block block = miningBlock(world, Material.COBBLESTONE);
        when(block.getType()).thenReturn(Material.COBBLESTONE, Material.IRON_ORE);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(block);
        BlockData original = mock(BlockData.class);
        when(original.getAsString()).thenReturn("minecraft:iron_ore");
        BlockData restored = mock(BlockData.class);
        Path journal = temporaryDirectory.resolve("mining-restorations.yml");
        MiningRestorationStore store = new MiningRestorationStore(
                temporaryDirectory.toFile(), Logger.getLogger("MiningRestorationStoreTest"));
        assertTrue(store.record(block, original, Material.COBBLESTONE, 0L));
        Files.delete(journal);
        Files.createDirectory(journal);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("StillCliff")).thenReturn(world);
            bukkit.when(() -> Bukkit.createBlockData("minecraft:iron_ore")).thenReturn(restored);
            store.tick(1L, 8);
            Files.delete(journal);
            store.tick(2L, 8);
        }

        verify(block, times(1)).setBlockData(restored, false);
        assertTrue(Files.isRegularFile(journal));
    }

    @Test
    void tickBoundsInspectionAndRotatesDeferredEntries() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        long future = Long.MAX_VALUE;
        for (int index = 0; index < 20; index++) {
            String root = "blocks.world;" + index + ";64;0";
            yaml.set(root + ".world", "world");
            yaml.set(root + ".x", index);
            yaml.set(root + ".y", 64);
            yaml.set(root + ".z", 0);
            yaml.set(root + ".data", "minecraft:iron_ore");
            yaml.set(root + ".temporary", "COBBLESTONE");
            yaml.set(root + ".restore-at", future);
        }
        yaml.save(temporaryDirectory.resolve("mining-restorations.yml").toFile());
        MiningRestorationStore store = new MiningRestorationStore(
                temporaryDirectory.toFile(), Logger.getLogger("MiningRestorationStoreTest"));

        assertEquals(8, store.tick(0L, 8));
        assertEquals(8, store.tick(0L, 8));
        assertEquals(0, store.tick(0L, 0));
    }

    @Test
    void futureRestorationSchemaRejectsWritesWithoutReplacingTheFile() throws Exception {
        Path file = temporaryDirectory.resolve("mining-restorations.yml");
        String original = "schema-version: 2\nblocks: {}\nfuture-field: preserve-me\n";
        Files.writeString(file, original);
        MiningRestorationStore store = new MiningRestorationStore(
                temporaryDirectory.toFile(), Logger.getLogger("MiningRestorationStoreTest"));
        Block block = miningBlock(Material.IRON_ORE);
        BlockData data = mock(BlockData.class);
        when(data.getAsString()).thenReturn("minecraft:iron_ore");

        assertFalse(store.record(block, data, Material.COBBLESTONE, 0L));
        assertEquals(original, Files.readString(file));
    }

    private Block miningBlock(Material material) {
        World world = mock(World.class);
        when(world.getName()).thenReturn("StillCliff");
        return miningBlock(world, material);
    }

    private Block miningBlock(World world, Material material) {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(10);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(20);
        when(block.getLocation()).thenReturn(new Location(world, 10, 64, 20));
        when(block.getType()).thenReturn(material);
        return block;
    }
}
