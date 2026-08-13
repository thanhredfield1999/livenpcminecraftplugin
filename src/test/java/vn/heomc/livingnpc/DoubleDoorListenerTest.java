package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class DoubleDoorListenerTest {
    @Test
    void allowsDoorInteractionOnlyWhenNpcIsNearby() {
        World world = mock(World.class);
        Location door = new Location(world, 10, 64, 20);

        assertTrue(DoubleDoorListener.withinOpeningRange(new Location(world, 10.5, 64, 19.5), door));
        assertFalse(DoubleDoorListener.withinOpeningRange(new Location(world, 10.5, 64, 18.5), door));
        assertFalse(DoubleDoorListener.withinOpeningRange(new Location(world, 9.5, 64, 19.5), door));
    }

    @Test
    void rejectsMissingOrCrossWorldLocations() {
        World world = mock(World.class);
        World otherWorld = mock(World.class);
        Location door = new Location(world, 10, 64, 20);

        assertFalse(DoubleDoorListener.withinOpeningRange(null, door));
        assertFalse(DoubleDoorListener.withinOpeningRange(new Location(otherWorld, 10.5, 64, 20.5), door));
    }
}
