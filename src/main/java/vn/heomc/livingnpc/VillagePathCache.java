package vn.heomc.livingnpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

final class VillagePathCache {
    private final VillageStore villages;
    private final Map<String, ScanState> states = new HashMap<>();

    VillagePathCache(VillageStore villages) {
        this.villages = villages;
    }

    void tick(long serverTick, LivingNpcConfig config) {
        ResidentPatrolSettings settings = config.residentPatrol();
        if (!settings.enabled()) return;
        for (VillageDefinition village : villages.villages()) {
            Location center = village.center().resolve();
            if (center == null || center.getWorld().getNearbyPlayers(center, config.activationRange()).isEmpty()) continue;
            ScanState state = states.computeIfAbsent(village.id(), ignored -> new ScanState());
            if (!state.scanning && serverTick >= state.nextScanTick) state.begin(settings.scanRadius());
            if (state.scanning) scanColumns(center, state, settings, serverTick);
        }
    }

    Location target(String villageId, Location current, ResidentPatrolSettings settings) {
        ScanState state = states.get(villageId);
        if (state == null || state.paths.isEmpty()) return null;
        double minimum = settings.minTargetDistance() * settings.minTargetDistance();
        double maximum = settings.maxTargetDistance() * settings.maxTargetDistance();
        List<Location> candidates = state.paths.stream()
                .filter(location -> location.getWorld().equals(current.getWorld()))
                .filter(location -> {
                    double distance = current.distanceSquared(location);
                    return distance >= minimum && distance <= maximum;
                }).toList();
        return candidates.isEmpty() ? null
                : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).clone();
    }

    private void scanColumns(
            Location center, ScanState state, ResidentPatrolSettings settings, long serverTick) {
        int diameter = settings.scanRadius() * 2 + 1;
        int totalColumns = diameter * diameter;
        int processed = 0;
        while (state.column < totalColumns && processed++ < settings.scanBlocksPerTick()) {
            int x = state.column % diameter - settings.scanRadius();
            int z = state.column / diameter - settings.scanRadius();
            state.column++;
            int blockX = center.getBlockX() + x;
            int blockZ = center.getBlockZ() + z;
            if (!center.getWorld().isChunkLoaded(blockX >> 4, blockZ >> 4)) continue;
            for (int y = settings.verticalRange(); y >= -settings.verticalRange(); y--) {
                Block path = center.getWorld().getBlockAt(blockX, center.getBlockY() + y, blockZ);
                if (path.getType() != Material.DIRT_PATH) continue;
                Block feet = path.getRelative(0, 1, 0);
                if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable()) {
                    Location location = feet.getLocation().add(0.5, 0, 0.5);
                    state.seenPaths++;
                    if (state.pending.size() < settings.maxCachedPaths()) {
                        state.pending.add(location);
                    } else {
                        int replacement = ThreadLocalRandom.current().nextInt(state.seenPaths);
                        if (replacement < settings.maxCachedPaths()) state.pending.set(replacement, location);
                    }
                    break;
                }
            }
        }
        if (state.column >= totalColumns) {
            state.paths = List.copyOf(state.pending);
            state.pending.clear();
            state.scanning = false;
            state.nextScanTick = serverTick + settings.scanIntervalTicks();
        }
    }

    private static final class ScanState {
        private List<Location> paths = List.of();
        private final List<Location> pending = new ArrayList<>();
        private long nextScanTick;
        private int column;
        private boolean scanning;
        private int seenPaths;

        private void begin(int radius) {
            pending.clear();
            column = 0;
            seenPaths = 0;
            scanning = true;
        }
    }
}
