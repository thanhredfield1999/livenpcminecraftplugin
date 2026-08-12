package vn.heomc.livingnpc;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;

final class SeatValidator {
    private SeatValidator() {
    }

    static SeatValidation validate(Block block) {
        if (block == null || !(block.getBlockData() instanceof Stairs stairs)) {
            return SeatValidation.invalid("Block được chọn phải là cầu thang (Stair).");
        }
        if (!block.getRelative(BlockFace.UP).isPassable()
                || !block.getRelative(BlockFace.UP, 2).isPassable()) {
            return SeatValidation.invalid("Phía trên ghế phải thoáng đủ 2 block.");
        }
        BlockFace front = stairs.getFacing().getOppositeFace();
        if (approachLocation(block, front) == null) {
            return SeatValidation.invalid("Không tìm được ô đứng an toàn cạnh ghế.");
        }
        SeatType type = isTable(block.getRelative(front)) ? SeatType.DINING : SeatType.REST;
        Location location = block.getLocation().add(0.5, 0.5, 0.5);
        location.setYaw(yaw(front));
        location.setPitch(0.0f);
        return SeatValidation.valid(new SeatDefinition(
                UUID.randomUUID().toString(), StoredLocation.from(location), type));
    }

    static Location approachLocation(SeatDefinition seat) {
        Location location = seat.location().resolve();
        if (location == null) return null;
        Block block = location.getBlock();
        if (!(block.getBlockData() instanceof Stairs stairs)) return null;
        return approachLocation(block, stairs.getFacing().getOppositeFace());
    }

    static boolean stillValid(SeatDefinition seat) {
        if (seat == null) return false;
        Location location = seat.location().resolve();
        if (location == null || !(location.getBlock().getBlockData() instanceof Stairs stairs)) return false;
        Block block = location.getBlock();
        if (!block.getRelative(BlockFace.UP).isPassable()
                || !block.getRelative(BlockFace.UP, 2).isPassable()) return false;
        BlockFace front = stairs.getFacing().getOppositeFace();
        SeatType currentType = isTable(block.getRelative(front)) ? SeatType.DINING : SeatType.REST;
        return currentType == seat.type() && approachLocation(block, front) != null;
    }

    static float yaw(BlockFace face) {
        return switch (face) {
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            case NORTH -> 180.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
    }

    private static Location approachLocation(Block seat, BlockFace preferred) {
        BlockFace[] candidates = {preferred, preferred.getOppositeFace(), rotateLeft(preferred), rotateRight(preferred)};
        for (BlockFace face : candidates) {
            Block feet = seat.getRelative(face);
            if (feet.isPassable() && feet.getRelative(BlockFace.UP).isPassable()
                    && feet.getRelative(BlockFace.DOWN).getType().isSolid()) {
                return feet.getLocation().add(0.5, 0.0, 0.5);
            }
        }
        return null;
    }

    private static boolean isTable(Block block) {
        return block.getType().isSolid() && !block.isPassable();
    }

    private static BlockFace rotateLeft(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> BlockFace.NORTH;
        };
    }

    private static BlockFace rotateRight(BlockFace face) {
        return rotateLeft(face).getOppositeFace();
    }
}
