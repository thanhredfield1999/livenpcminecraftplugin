package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MovementIntentPolicyTest {
    @Test
    void storageIntentRequiresConfiguredGatePolicy() {
        assertTrue(MovementIntentPolicy.requiresConfiguredGate(MovementIntent.GO_TO_STORAGE));
        assertTrue(MovementIntentPolicy.requiresConfiguredGate(MovementIntent.GOING_TO_BED));
        assertFalse(MovementIntentPolicy.requiresConfiguredGate(MovementIntent.WANDERING));
    }

    @Test
    void movementIntentRejectsDifferentWorld() {
        World first = Mockito.mock(World.class);
        World second = Mockito.mock(World.class);
        assertFalse(MovementIntentPolicy.valid(
                MovementIntent.GOING_HOME,
                new Location(first, 0, 0, 0),
                new Location(second, 1, 0, 1)));
    }

    @Test
    void movementIntentAcceptsFiniteSameWorldTarget() {
        World world = Mockito.mock(World.class);
        assertTrue(MovementIntentPolicy.valid(
                MovementIntent.GOING_TO_PLOT,
                new Location(world, 0, 0, 0),
                new Location(world, 10, 2, 3)));
    }
}
