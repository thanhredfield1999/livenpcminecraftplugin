package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class CombatManagerBedtimeTest {
    @Test
    void activeArenaIsPreemptedDuringBedtimeWindow() {
        CombatArena arena = arena();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getTime()).thenReturn(18000L);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            assertTrue(CombatManager.isBedtimeFor(arena));
        }
    }

    @Test
    void arenaKeepsFightingOutsideTheBedtimeWindow() {
        CombatArena arena = arena();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            when(world.getTime()).thenReturn(12000L);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            assertFalse(CombatManager.isBedtimeFor(arena));
        }
    }

    @Test
    void unresolvedCornerWorldNeverPreempts() {
        CombatArena arena = arena();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(null);

            assertFalse(CombatManager.isBedtimeFor(arena));
        }
    }

    private static CombatArena arena() {
        UUID archer = new UUID(0L, 0L);
        UUID swordsman = new UUID(0L, 1L);
        StoredLocation corner = new StoredLocation("world", 10, 64, 10, 0, 0);
        StoredLocation opposite = new StoredLocation("world", 20, 64, 20, 0, 0);
        StoredLocation retreat = new StoredLocation("world", 15, 64, 15, 0, 0);
        return new CombatArena("gate", "village-a", archer, swordsman,
                corner, opposite, retreat, true, 0);
    }
}
