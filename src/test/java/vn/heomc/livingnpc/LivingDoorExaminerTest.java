package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.junit.jupiter.api.Test;

class LivingDoorExaminerTest {
    @Test
    void callbackIgnoresMissingPathBlockInsteadOfThrowingCitizensNpe() {
        NPC npc = mock(NPC.class);

        assertDoesNotThrow(() -> new LivingDoorExaminer.DoorOpener()
                .run(npc, null, List.of(), 0));
    }

    @Test
    void delayedCloseIgnoresBlockThatIsNoLongerOpenable() {
        Block stale = mock(Block.class);
        when(stale.getType()).thenReturn(Material.STONE);
        when(stale.getBlockData()).thenReturn(mock(BlockData.class));

        assertFalse(LivingDoorExaminer.closeIfStillOpen(stale, Material.OAK_DOOR));
    }

    @Test
    void delayedCloseOnlyClosesTheSameOpenableMaterial() {
        Block door = mock(Block.class);
        Openable open = mock(Openable.class);
        when(door.getType()).thenReturn(Material.OAK_DOOR);
        when(door.getBlockData()).thenReturn(open);
        when(open.isOpen()).thenReturn(true);

        assertTrue(LivingDoorExaminer.closeIfStillOpen(door, Material.OAK_DOOR));
        verify(open).setOpen(false);
        verify(door).setBlockData(open);
    }

    @Test
    void successfulOpenOwnsACloseTaskImmediatelyWithoutWaitingForOnReached() {
        NPC npc = mock(NPC.class);
        Block door = mock(Block.class);
        World world = mock(World.class);
        AtomicInteger scheduled = new AtomicInteger();
        when(npc.getStoredLocation()).thenReturn(new Location(world, 0.5, 64, 0.5));
        when(door.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(door.getType()).thenReturn(Material.OAK_DOOR);
        LivingDoorExaminer.DoorOpener opener = new LivingDoorExaminer.DoorOpener(
                (ignoredNpc, ignoredBlock, ignoredMaterial) -> true,
                (ignoredNpc, ignoredBlock, ignoredMaterial) -> scheduled.incrementAndGet(),
                ignoredMaterial -> true);

        opener.run(npc, door, List.of(door), 0);
        opener.onReached(npc, door);

        assertEquals(1, scheduled.get());
    }

    @Test
    void closeStateWaitsUntilNpcEntersThenLeavesCloseRadius() {
        LivingDoorExaminer.CloseState state = new LivingDoorExaminer.CloseState();

        assertFalse(state.shouldClose(true, true, true, true, 4.0, 1));
        assertFalse(state.shouldClose(true, true, true, true, 1.0, 2));
        assertTrue(state.shouldClose(true, true, true, true, 4.0, 3));
    }

    @Test
    void closeStateStillClosesOnNavigationEndOrTimeout() {
        assertTrue(new LivingDoorExaminer.CloseState()
                .shouldClose(true, false, true, true, 4.0, 1));
        assertTrue(new LivingDoorExaminer.CloseState()
                .shouldClose(true, true, true, true, 4.0, 240));
    }

    @Test
    void closeStateDoesNotCloseOntoNpcWhenNavigationEndsAtTheDoor() {
        LivingDoorExaminer.CloseState state = new LivingDoorExaminer.CloseState();

        assertFalse(state.shouldClose(true, false, true, true, 1.0, 1));
        assertTrue(state.shouldClose(true, false, true, true, 4.0, 2));
    }

    @Test
    void closeStateDoesNotCloseOntoNpcWhenOpeningNavigationIsReplaced() {
        LivingDoorExaminer.CloseState state = new LivingDoorExaminer.CloseState();

        assertFalse(state.shouldClose(true, true, true, false, 1.0, 1));
        assertTrue(state.shouldClose(true, true, true, false, 4.0, 2));
    }

    @Test
    void schedulingFailureImmediatelyClosesTheBlockThatWasOpened() {
        NPC npc = mock(NPC.class);
        Block door = mock(Block.class);
        Openable open = mock(Openable.class);
        World world = mock(World.class);
        when(npc.getStoredLocation()).thenReturn(new Location(world, 0.5, 64, 0.5));
        when(door.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(door.getType()).thenReturn(Material.OAK_DOOR);
        when(door.getBlockData()).thenReturn(open);
        when(open.isOpen()).thenReturn(true);
        LivingDoorExaminer.CloseScheduler scheduler = mock(LivingDoorExaminer.CloseScheduler.class);
        doThrow(new IllegalStateException("scheduler stopped"))
                .when(scheduler).schedule(npc, door, Material.OAK_DOOR);
        LivingDoorExaminer.DoorOpener opener = new LivingDoorExaminer.DoorOpener(
                (ignoredNpc, ignoredBlock, ignoredMaterial) -> true,
                scheduler,
                ignoredMaterial -> true);

        assertDoesNotThrow(() -> opener.run(npc, door, List.of(door), 0));

        verify(open).setOpen(false);
        verify(door).setBlockData(open);
    }
}