package vn.heomc.livingnpc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Gate;

final class GateRouteDiscovery {
    private static final int MAX_ROUTE_DISTANCE = 96;
    private static final int CORRIDOR_RADIUS = 8;
    private static final int VERTICAL_RANGE = 4;
    private static final int MAX_CANDIDATES = 16;

    private GateRouteDiscovery() {
    }

    static List<GateRoute.Candidate> discover(
            Location current, Location target, List<?> configuredGates) {
        if (configuredGates == null) return List.of();
        return discover(current, target, configuredGates.stream()
                        .map(value -> value instanceof NavigationGate gate ? gate
                                : new NavigationGate((StoredLocation) value, null)).toList(), ResidentRole.FARMER);
    }

    static List<GateRoute.Candidate> discover(
                Location current, Location target, List<?> configuredGates, ResidentRole role) {
            List<NavigationGate> gates = configuredGates == null ? List.of() : configuredGates.stream()
                    .map(value -> value instanceof NavigationGate gate ? gate
                            : new NavigationGate((StoredLocation) value, null))
                    .toList();
        if (current == null || target == null || current.getWorld() == null
                || !current.getWorld().equals(target.getWorld())) return List.of();
        World world = current.getWorld();
        double dx = target.getX() - current.getX();
        double dz = target.getZ() - current.getZ();
        if (dx * dx + dz * dz > MAX_ROUTE_DISTANCE * MAX_ROUTE_DISTANCE) return List.of();

        int minY = Math.min(current.getBlockY(), target.getBlockY()) - VERTICAL_RANGE;
        int maxY = Math.max(current.getBlockY(), target.getBlockY()) + VERTICAL_RANGE;
        if (maxY - minY > VERTICAL_RANGE * 4) return List.of();

        ArrayList<ScoredCandidate> found = new ArrayList<>();
        for (NavigationGate configured : gates) {
            if (!configured.allows(role)) continue;
            StoredLocation location = configured.location();
            if (!world.getName().equals(location.world())) continue;
            int x = (int) Math.floor(location.x());
            int y = (int) Math.floor(location.y());
            int z = (int) Math.floor(location.z());
            boolean currentGate = horizontalDistance(current,
                    new Location(world, x + 0.5, y, z + 0.5)) <= 1.5;
            if (y < minY || y > maxY || (!currentGate
                    && horizontalDistance(current, new Location(world, x + 0.5, y, z + 0.5)) > MAX_ROUTE_DISTANCE)
                    || !world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block block = world.getBlockAt(x, y, z);
            if (!(block.getBlockData() instanceof Gate gate)) continue;
            GateRoute.Candidate candidate = candidate(world, x, y, z, gate.getFacing(), current);
            if (candidate == null || !movesTowardTarget(candidate, target)) continue;
            double detour = horizontalDistance(current, candidate.approach())
                    + horizontalDistance(candidate.approach(), candidate.exit())
                    + horizontalDistance(candidate.exit(), target);
            found.add(new ScoredCandidate(candidate, detour));
        }
        return found.stream()
                .sorted(Comparator.comparingDouble(ScoredCandidate::detour)
                        .thenComparing(scored -> scored.candidate().key()))
                .limit(MAX_CANDIDATES)
                .map(ScoredCandidate::candidate)
                .toList();
    }

    private static GateRoute.Candidate candidate(
            World world, int x, int y, int z, BlockFace facing, Location current) {
        if (facing == null || (facing.getModX() == 0 && facing.getModZ() == 0)) return null;
        Location first = standingLocation(
                world, x - facing.getModX(), y, z - facing.getModZ());
        Location second = standingLocation(
                world, x + facing.getModX(), y, z + facing.getModZ());
        if (first == null || second == null) return null;
        Location gateSide = horizontalDistance(current, first) <= horizontalDistance(current, second)
                ? first : second;
        Location exit = gateSide == first ? second : first;
        int outwardX = Integer.signum(gateSide.getBlockX() - x);
        int outwardZ = Integer.signum(gateSide.getBlockZ() - z);
        Location approach = standingLocation(
                world, gateSide.getBlockX() + outwardX, y, gateSide.getBlockZ() + outwardZ);
        if (approach == null) return null;
        String key = gateKey(world, x, y, z);
        return new GateRoute.Candidate(key, approach, exit);
    }

    static String gateKey(Block block) {
        return gateKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    private static String gateKey(World world, int x, int y, int z) {
        return world.getName() + ":" + x + ":" + y + ":" + z;
    }

    private static Location standingLocation(World world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block floor = world.getBlockAt(x, y - 1, z);
        if (!feet.isPassable() || !head.isPassable() || floor.isPassable()) return null;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private static double horizontalDistance(Location first, Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean movesTowardTarget(GateRoute.Candidate candidate, Location target) {
        return horizontalDistance(candidate.exit(), target) < horizontalDistance(candidate.approach(), target);
    }

    private static double distanceToSegmentSquared(
            double x, double z, Location start, Location end) {
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared == 0.0) {
            double px = x - start.getX();
            double pz = z - start.getZ();
            return px * px + pz * pz;
        }
        double projection = ((x - start.getX()) * dx + (z - start.getZ()) * dz) / lengthSquared;
        double clamped = Math.max(0.0, Math.min(1.0, projection));
        double nearestX = start.getX() + clamped * dx;
        double nearestZ = start.getZ() + clamped * dz;
        double px = x - nearestX;
        double pz = z - nearestZ;
        return px * px + pz * pz;
    }

    private static boolean liesBetween(Location start, Location end, double x, double z) {
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared == 0.0) return false;
        double projection = ((x - start.getX()) * dx + (z - start.getZ()) * dz) / lengthSquared;
        return projection > 0.0 && projection < 1.0;
    }

    private record ScoredCandidate(GateRoute.Candidate candidate, double detour) {
    }
}
