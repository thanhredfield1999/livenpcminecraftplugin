package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FisherTargetPolicyTest {
    @Test
    void targetKeyUsesStandingBlockCoordinates() {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("StillCliff");

        assertEquals("StillCliff:12:64:-4", FisherRuntime.targetKey(
                new Location(world, 12.9, 64.2, -3.1)));
    }
}
