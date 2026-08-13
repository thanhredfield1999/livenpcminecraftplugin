package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class DoubleDoorListenerTest {
    @Test
    void allowsDoorInteractionOnlyWhenNpcIsNearby() {
        World world = mock(World.class);
        Location door = new Location(world, 10, 64, 20);

        assertTrue(DoubleDoorListener.withinOpeningRange(new Location(world, 10.5, 64, 18.5), door));
        assertFalse(DoubleDoorListener.withinOpeningRange(new Location(world, 10.5, 64, 17.5), door));
    }

    @Test
    void rejectsMissingOrCrossWorldLocations() {
        World world = mock(World.class);
        World otherWorld = mock(World.class);
        Location door = new Location(world, 10, 64, 20);

        assertFalse(DoubleDoorListener.withinOpeningRange(null, door));
        assertFalse(DoubleDoorListener.withinOpeningRange(new Location(otherWorld, 10.5, 64, 20.5), door));
    }

    @Test
    void centresPassageOnOppositeSidesOfDoor() {
        World world = mock(World.class);
        Location door = new Location(world, 10, 64, 20);

        DoubleDoorListener.DoorSides north = DoubleDoorListener.doorSides(
                new Location(world, 10.5, 64, 18.5), door, BlockFace.NORTH);
        DoubleDoorListener.DoorSides south = DoubleDoorListener.doorSides(
                new Location(world, 10.5, 64, 22.5), door, BlockFace.NORTH);

        assertTrue(north.before().equals(new Location(world, 10.5, 64, 19.5)));
        assertTrue(north.after().equals(new Location(world, 10.5, 64, 21.5)));
        assertTrue(south.before().equals(new Location(world, 10.5, 64, 21.5)));
        assertTrue(south.after().equals(new Location(world, 10.5, 64, 19.5)));
    }
}
