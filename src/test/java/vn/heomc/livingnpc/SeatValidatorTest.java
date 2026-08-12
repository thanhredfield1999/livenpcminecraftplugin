package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class SeatValidatorTest {
    @Test
    void mapsCardinalSeatFacingToMinecraftYaw() {
        assertEquals(0.0f, SeatValidator.yaw(BlockFace.SOUTH));
        assertEquals(90.0f, SeatValidator.yaw(BlockFace.WEST));
        assertEquals(180.0f, SeatValidator.yaw(BlockFace.NORTH));
        assertEquals(-90.0f, SeatValidator.yaw(BlockFace.EAST));
    }
}
