package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class MovementServiceTest {
    @Test
    void mapsStoragePhaseToStorageIntent() {
        assertEquals(MovementIntent.GO_TO_STORAGE, MovementService.intentFor(FarmerPhase.GOING_TO_STORAGE));
    }

    @Test
    void rejectsInvalidMovementBeforeCitizensNavigation() {
        World world = mock(World.class);
        assertFalse(MovementService.valid(
                FarmerPhase.GOING_HOME,
                new Location(world, 0, 0, 0),
                new Location(mock(World.class), 1, 0, 1)));
    }

    @Test
    void acceptsSameWorldFiniteMovement() {
        World world = mock(World.class);
        assertTrue(MovementService.valid(
                FarmerPhase.GOING_TO_PLOT,
                new Location(world, 0, 0, 0),
                new Location(world, 12, 3, -4)));
    }

    @Test
    void reappliesLocalParametersWhenSameTargetKeepsCurrentNavigation() {
        Navigator navigator = mock(Navigator.class);
        NavigatorParameters parameters = new NavigatorParameters();
        World world = mock(World.class);
        Location target = new Location(world, 4, 65, 8);
        when(navigator.isNavigating()).thenReturn(true);
        when(navigator.getTargetAsLocation()).thenReturn(target);
        when(navigator.getLocalParameters()).thenReturn(parameters);

        assertTrue(MovementService.startSimpleNavigation(navigator, target, 0.75F, 1.4));

        verify(navigator, org.mockito.Mockito.never()).setTarget(target);
        assertEquals(0.75F, parameters.speedModifier());
        assertEquals(1.4, parameters.distanceMargin());
        assertEquals(1.4, parameters.pathDistanceMargin());
    }
}
