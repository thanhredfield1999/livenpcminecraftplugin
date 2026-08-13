package vn.heomc.livingnpc;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;

final class DoubleDoorSupport {
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private DoubleDoorSupport() {
    }

    static Block bottom(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Door door && door.getHalf() == Bisected.Half.TOP) {
            return block.getRelative(BlockFace.DOWN);
        }
        return block;
    }

    static Block findPartner(Block block) {
        Block doorBlock = bottom(block);
        if (!(doorBlock.getBlockData() instanceof Door door) || door.getHalf() != Bisected.Half.BOTTOM) {
            return null;
        }
        for (BlockFace face : HORIZONTAL_FACES) {
            Block candidate = doorBlock.getRelative(face);
            if (!(candidate.getBlockData() instanceof Door other)) continue;
            if (isPair(doorBlock, door, candidate, other)) return candidate;
        }
        return null;
    }

    private static boolean isPair(Block firstBlock, Door first, Block secondBlock, Door second) {
        if (firstBlock.getType() != secondBlock.getType()
                || second.getHalf() != Bisected.Half.BOTTOM
                || first.getFacing() != second.getFacing()
                || first.getHinge() == second.getHinge()) {
            return false;
        }
        int dx = secondBlock.getX() - firstBlock.getX();
        int dz = secondBlock.getZ() - firstBlock.getZ();
        return Math.abs(dx) + Math.abs(dz) == 1
                && dx * first.getFacing().getModX() + dz * first.getFacing().getModZ() == 0;
    }
}
