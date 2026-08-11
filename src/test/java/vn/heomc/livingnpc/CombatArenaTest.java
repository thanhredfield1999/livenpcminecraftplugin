package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CombatArenaTest {
    @Test
    void containsOnlyLocationsInsideConfiguredCuboid() {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("StillCliff");
        CombatArena arena = new CombatArena("zombie", "stillcliff_1", UUID.randomUUID(), UUID.randomUUID(),
                new StoredLocation("StillCliff", 0, 10, 0, 0, 0),
                new StoredLocation("StillCliff", 10, 20, 10, 0, 0),
                new StoredLocation("StillCliff", -2, 10, -2, 0, 0), false, 0);

        assertTrue(arena.contains(new Location(world, 5, 15, 5)));
        assertFalse(arena.contains(new Location(world, 12, 15, 5)));
    }
}
