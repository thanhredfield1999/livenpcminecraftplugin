package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.junit.jupiter.api.Test;

class DoubleDoorSupportTest {
    @Test
    void findsAdjacentDoorWithMatchingFacingAndOppositeHinge() {
        Block left = door(10, 64, 20, BlockFace.NORTH, Door.Hinge.LEFT);
        Block right = door(11, 64, 20, BlockFace.NORTH, Door.Hinge.RIGHT);
        surround(left, right, BlockFace.EAST);

        assertSame(right, DoubleDoorSupport.findPartner(left));
    }

    @Test
    void ignoresSingleDoor() {
        Block door = door(10, 64, 20, BlockFace.NORTH, Door.Hinge.LEFT);
        surround(door, null, null);

        assertNull(DoubleDoorSupport.findPartner(door));
    }

    @Test
    void ignoresAdjacentDoorFacingAnotherDirection() {
        Block first = door(10, 64, 20, BlockFace.NORTH, Door.Hinge.LEFT);
        Block second = door(11, 64, 20, BlockFace.SOUTH, Door.Hinge.RIGHT);
        surround(first, second, BlockFace.EAST);

        assertNull(DoubleDoorSupport.findPartner(first));
    }

    @Test
    void ignoresAdjacentDoorWithSameHinge() {
        Block first = door(10, 64, 20, BlockFace.NORTH, Door.Hinge.LEFT);
        Block second = door(11, 64, 20, BlockFace.NORTH, Door.Hinge.LEFT);
        surround(first, second, BlockFace.EAST);

        assertNull(DoubleDoorSupport.findPartner(first));
    }

    @Test
    void normalizesTopHalfToBottomBlock() {
        Block top = mock(Block.class);
        Block bottom = door(10, 64, 20, BlockFace.NORTH, Door.Hinge.LEFT);
        Door topData = mock(Door.class);
        when(top.getBlockData()).thenReturn(topData);
        when(topData.getHalf()).thenReturn(Bisected.Half.TOP);
        when(top.getRelative(BlockFace.DOWN)).thenReturn(bottom);

        assertSame(bottom, DoubleDoorSupport.bottom(top));
    }

    private static Block door(int x, int y, int z, BlockFace facing, Door.Hinge hinge) {
        Block block = mock(Block.class);
        Door data = mock(Door.class);
        when(block.getType()).thenReturn(Material.OAK_DOOR);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getBlockData()).thenReturn(data);
        when(data.getHalf()).thenReturn(Bisected.Half.BOTTOM);
        when(data.getFacing()).thenReturn(facing);
        when(data.getHinge()).thenReturn(hinge);
        return block;
    }

    private static void surround(Block source, Block partner, BlockFace partnerFace) {
        for (BlockFace face : new BlockFace[] {
                BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
        }) {
            Block relative = face == partnerFace ? partner : mock(Block.class);
            when(source.getRelative(face)).thenReturn(relative);
        }
    }
}
