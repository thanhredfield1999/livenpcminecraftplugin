package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.ai.Navigator;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class NavigationRecoveryTest {
    @Test
    void recoversToSafePointNearExactTaskTarget() {
        World world = mock(World.class);
        NPC npc = mock(NPC.class);
        org.bukkit.entity.Entity entity = mock(org.bukkit.entity.Entity.class);
        Location current = new Location(world, 0, 64, 0);
        Location target = new Location(world, 10, 64, 10);
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getUniqueId()).thenReturn(UUID.randomUUID());
        when(npc.getEntity()).thenReturn(entity);
        when(npc.getNavigator()).thenReturn(mock(Navigator.class));
        when(entity.getLocation()).thenReturn(current);
        when(entity.teleport(any(Location.class))).thenReturn(true);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block feet = mock(Block.class);
        Block head = mock(Block.class);
        Block support = mock(Block.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(feet);
        when(feet.isPassable()).thenReturn(true);
        when(feet.isLiquid()).thenReturn(false);
        when(feet.getRelative(0, 1, 0)).thenReturn(head);
        when(feet.getRelative(0, -1, 0)).thenReturn(support);
        when(head.isPassable()).thenReturn(true);
        when(head.isLiquid()).thenReturn(false);
        when(support.getType()).thenReturn(org.bukkit.Material.STONE);
        when(feet.getLocation()).thenReturn(new Location(world, 10, 64, 9));

        assertEquals(NavigationRecovery.Result.RECOVERED,
                NavigationRecovery.recover(npc, target, 2, 80, "TASK", 60));
        verify(entity).teleport(any(Location.class));
    }

    @Test
    void recoveryUnavailableRetainsIntentAndReportsBackoff() {
        World world = mock(World.class);
        NPC npc = mock(NPC.class);
        org.bukkit.entity.Entity entity = mock(org.bukkit.entity.Entity.class);
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getUniqueId()).thenReturn(UUID.randomUUID());
        when(npc.getEntity()).thenReturn(entity);
        when(npc.getNavigator()).thenReturn(mock(Navigator.class));
        when(entity.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

        assertEquals(NavigationRecovery.Result.UNAVAILABLE,
                NavigationRecovery.recover(npc, new Location(world, 10, 64, 10), 2, 80, "TASK", 60));
        verify(entity, never()).teleport(any(Location.class));
    }

    @Test
    void bedRecoveryNeverCreatesSleepState() {
        World world = mock(World.class);
        NPC npc = mock(NPC.class);
        org.bukkit.entity.Entity entity = mock(org.bukkit.entity.Entity.class);
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getUniqueId()).thenReturn(UUID.randomUUID());
        when(npc.getEntity()).thenReturn(entity);
        when(npc.getNavigator()).thenReturn(mock(Navigator.class));
        when(entity.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

        assertEquals(NavigationRecovery.Result.UNAVAILABLE,
                NavigationRecovery.recover(npc, new Location(world, 10, 64, 10), 2, 80, "GOING_TO_BED", 60));
        verify(entity, never()).teleport(any(Location.class));
    }

    @Test
    void recoveredResultLeavesIntentForCallerToContinue() {
        assertTrue(NavigationRecovery.Result.RECOVERED.continueIntent());
        assertTrue(NavigationRecovery.Result.UNAVAILABLE.retainIntent());
    }

    @Test
    void safeStandingSearchAcceptsSupportedBlockAboveOrBelowTargetY() {
        World world = mock(World.class);
        Location target = new Location(world, 10, 64, 10);
        Location current = new Location(world, 10, 65, 10);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int y = invocation.getArgument(1);
            Block feet = mock(Block.class);
            Block head = mock(Block.class);
            Block support = mock(Block.class);
            when(feet.isPassable()).thenReturn(y == 65);
            when(feet.isLiquid()).thenReturn(false);
            when(feet.getRelative(0, 1, 0)).thenReturn(head);
            when(feet.getRelative(0, -1, 0)).thenReturn(support);
            when(head.isPassable()).thenReturn(true);
            when(head.isLiquid()).thenReturn(false);
            when(support.getType()).thenReturn(Material.STONE);
            when(feet.getLocation()).thenReturn(new Location(world, 10, y, 10));
            return feet;
        });

        Location standing = NavigationRecovery.findSafeStanding(target, current, 1);

        assertNotNull(standing);
        assertEquals(65.0, standing.getY());
    }
}