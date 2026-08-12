package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class FisherCastGeometryTest {
    @Test
    void castVelocityReachesSelectedWaterAfterEightTicks() {
        Location origin = new Location(null, 0.5, 2.6, 0.5);
        Location water = new Location(null, 0.5, 0.9, 3.0);

        Vector velocity = FisherRuntime.castVelocity(origin, water);
        double ticks = 8.0;
        Vector reached = origin.toVector().add(velocity.clone().multiply(ticks))
                .add(new Vector(0, -0.5 * 0.03 * ticks * ticks, 0));

        assertEquals(water.getX(), reached.getX(), 0.0001);
        assertEquals(water.getY(), reached.getY(), 0.0001);
        assertEquals(water.getZ(), reached.getZ(), 0.0001);
        assertEquals(2.5, Math.hypot(water.getX() - origin.getX(), water.getZ() - origin.getZ()), 0.0001);
    }

    @Test
    void horizontalDistanceIgnoresHeight() {
        Location first = new Location(null, 1.0, 30.0, 1.0);
        Location second = new Location(null, 3.0, -10.0, 3.0);

        assertEquals(8.0, FisherRuntime.horizontalDistanceSquared(first, second));
    }
}
