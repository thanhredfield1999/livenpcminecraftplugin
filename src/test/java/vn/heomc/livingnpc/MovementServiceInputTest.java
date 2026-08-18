package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import net.citizensnpcs.api.ai.Navigator;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class MovementServiceInputTest {
    @Test
    void rejectsNonFiniteOrNonPositiveSpeedBeforeNavigation() {
        Navigator navigator = mock(Navigator.class);
        World world = mock(World.class);
        Location target = new Location(world, 1, 64, 1);

        assertFalse(MovementService.startSimpleNavigation(navigator, target, Float.NaN, 1.0));
        assertFalse(MovementService.startSimpleNavigation(navigator, target, Float.POSITIVE_INFINITY, 1.0));
        assertFalse(MovementService.startSimpleNavigation(navigator, target, 0.0F, 1.0));
        assertFalse(MovementService.startSimpleNavigation(navigator, target, -0.5F, 1.0));
    }
}
