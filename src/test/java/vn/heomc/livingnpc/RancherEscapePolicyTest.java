package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class RancherEscapePolicyTest {
    @Test
    void ranksEscapedAnimalsByHorizontalDistanceFromRanch() {
        Location center = new Location(null, 10.0, 64.0, 10.0);
        assertEquals(25.0, RancherRuntime.distanceOutsideSquared(
                new Location(null, 13.0, 100.0, 14.0), center));
    }
}
