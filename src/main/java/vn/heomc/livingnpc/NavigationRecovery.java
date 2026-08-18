package vn.heomc.livingnpc;

import java.util.Comparator;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

/** Timeout recovery: move only to validated, loaded standing space beside exact intent target. */
final class NavigationRecovery {

    private NavigationRecovery() {
    }

    static Result recover(NPC npc, Location target, int radius, long serverTick, String operation, long backoffTicks) {
        if (npc == null || !npc.isSpawned() || target == null || target.getWorld() == null) {
            return Result.UNAVAILABLE;
        }
        Location current = npc.getEntity().getLocation();
        if (current == null || current.getWorld() == null || !current.getWorld().equals(target.getWorld())
                || !RuntimeChunkAvailability.loaded(current)
                || !RuntimeChunkAvailability.loadedArea(target, radius)) {
            report(npc, target, operation, "RECOVERY_UNAVAILABLE", serverTick);
            return Result.UNAVAILABLE;
        }
        Navigator navigator = npc.getNavigator();
        if (navigator != null && navigator.isNavigating()) navigator.cancelNavigation();
        Location standing = findSafeStanding(target, current, radius);
        if (standing == null) {
            report(npc, target, operation, "RECOVERY_UNAVAILABLE", serverTick);
            return Result.UNAVAILABLE;
        }
        if (!npc.getEntity().teleport(standing)) {
            report(npc, target, operation, "RECOVERY_UNAVAILABLE", serverTick);
            return Result.UNAVAILABLE;
        }
        report(npc, target, operation, "RECOVERED_TO_SAFE_STANDING", serverTick);
        return Result.RECOVERED;
    }

    static Location findSafeStanding(Location target, Location current, int radius) {
        if (target == null || target.getWorld() == null) return null;
        int bounded = Math.max(1, radius);
        return java.util.stream.IntStream.rangeClosed(-bounded, bounded).boxed()
                .flatMap(x -> java.util.stream.IntStream.rangeClosed(-bounded, bounded)
                        .mapToObj(z -> new int[]{x, z}))
                .flatMap(offset -> java.util.stream.IntStream.rangeClosed(-2, 2)
                        .mapToObj(y -> target.getWorld().getBlockAt(
                                target.getBlockX() + offset[0], target.getBlockY() + y,
                                target.getBlockZ() + offset[1])))
                .filter(NavigationRecovery::safe)
                .map(Block::getLocation)
                .map(location -> location.add(0.5, 0, 0.5))
                .min(Comparator.comparingDouble(location -> current == null
                        ? 0.0 : current.distanceSquared(location)))
                .orElse(null);
    }

    private static boolean safe(Block feet) {
        Material support = feet.getRelative(0, -1, 0).getType();
        return feet.isPassable() && !feet.isLiquid()
                && feet.getRelative(0, 1, 0).isPassable()
                && !feet.getRelative(0, 1, 0).isLiquid()
                && support != null && support != Material.AIR && support != Material.CAVE_AIR
                && support != Material.VOID_AIR && support != Material.MAGMA_BLOCK
                && support != Material.CAMPFIRE && support != Material.SOUL_CAMPFIRE
                && support != Material.CACTUS;
    }

    private static void report(NPC npc, Location target, String operation, String reason, long tick) {
        try {
            NavigationDiagnostics.shared().recordNavigationRecovery(npc, operation, reason, target, 0L);
        } catch (Throwable ignored) {
            // Recovery outcome remains explicit; diagnostics must not break intent retention.
        }
    }


    enum Result {
        RECOVERED(true, false),
        UNAVAILABLE(false, true);

        private final boolean continueIntent;
        private final boolean retainIntent;

        Result(boolean continueIntent, boolean retainIntent) {
            this.continueIntent = continueIntent;
            this.retainIntent = retainIntent;
        }

        boolean continueIntent() {
            return continueIntent;
        }

        boolean retainIntent() {
            return retainIntent;
        }
    }
}
