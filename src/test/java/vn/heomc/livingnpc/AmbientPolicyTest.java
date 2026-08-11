package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AmbientPolicyTest {
    @Test
    void noticesNearbyPlayerFirst() {
        assertEquals(AmbientAction.WATCH_PLAYER, AmbientPolicy.choose(10, true, true, true, true));
        assertEquals(AmbientAction.WANDER, AmbientPolicy.choose(20, true, true, true, true));
    }

    @Test
    void doesNotWatchWhenNoPlayerIsNearby() {
        assertEquals(AmbientAction.WANDER, AmbientPolicy.choose(10, false, true, true, true));
    }

    @Test
    void fallsBackWhenWanderingIsUnavailable() {
        assertEquals(AmbientAction.LOOK_AROUND, AmbientPolicy.choose(40, false, false, true, true));
        assertEquals(AmbientAction.LOOK_AROUND, AmbientPolicy.choose(69, true, false, true, true));
        assertEquals(AmbientAction.REST, AmbientPolicy.choose(70, true, false, true, true));
        assertEquals(AmbientAction.REST, AmbientPolicy.choose(90, true, false, true, true));
        assertNull(AmbientPolicy.choose(50, false, false, false, false));
    }
}
