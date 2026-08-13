package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeatValidatorTest {
    @Test
    void mapsCardinalSeatFacingToMinecraftYaw() {
        assertEquals(0.0f, SeatValidator.yaw(BlockFace.SOUTH));
        assertEquals(90.0f, SeatValidator.yaw(BlockFace.WEST));
        assertEquals(180.0f, SeatValidator.yaw(BlockFace.NORTH));
        assertEquals(-90.0f, SeatValidator.yaw(BlockFace.EAST));
    }

    @Test
    void seatedNpcFacesAwayFromTheRaisedBackOfTheStair() {
        Stairs stairs = mock(Stairs.class);
        when(stairs.getFacing()).thenReturn(BlockFace.NORTH);

        assertEquals(BlockFace.SOUTH, SeatValidator.sittingFacing(stairs));
    }
}
