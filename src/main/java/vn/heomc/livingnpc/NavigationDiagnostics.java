package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.logging.Logger;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.PathStrategy;
import net.citizensnpcs.api.ai.event.CancelReason;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;

final class NavigationDiagnostics {
    private static final long LOG_INTERVAL_TICKS = 100L;
    private static NavigationDiagnostics shared = new NavigationDiagnostics(Logger.getLogger("LivingNPC"));

    private final Logger logger;
    private final Map<LogKey, Long> nextLogTick = new HashMap<>();

    NavigationDiagnostics(Logger logger) {
        this.logger = logger;
    }

    static void initialize(Logger logger) {
        shared = new NavigationDiagnostics(logger);
    }

    static NavigationDiagnostics shared() {
        return shared;
    }

    NavigatorParameters activeParametersAfterTarget(
            Navigator navigator, Location target, double distanceMargin) {
        navigator.setTarget(target);
        return navigator.getLocalParameters()
                .distanceMargin(distanceMargin)
                .pathDistanceMargin(distanceMargin);
    }

    void logFisherHook(String message) {
        logger.info(message);
    }

    boolean targetInRange(Location current, Location target, float range) {
        return current != null && target != null && current.getWorld() != null
                && current.getWorld().equals(target.getWorld())
                && current.distanceSquared(target) <= (double) range * range;
    }

    void attach(
            NPC npc, NavigatorParameters parameters, String operation, Location target,
            double distanceMargin, double pathMargin) {
        int startedTick = Bukkit.getCurrentTick();
        Location snapshot = target.clone();
        parameters.addSingleUseCallback(reason -> log(
                npc, operation, reasonName(reason), snapshot, distanceMargin, pathMargin,
                parameters, Math.max(0L, (long) Bukkit.getCurrentTick() - startedTick)));
    }

    void targetOutOfRange(
            NPC npc, NavigatorParameters parameters, String operation, Location target,
            double distanceMargin, double pathMargin) {
        log(npc, operation, "TARGET_OUT_OF_RANGE", target, distanceMargin, pathMargin, parameters, 0L);
    }

    static String reasonName(CancelReason reason) {
        return reason == null ? "COMPLETED" : reason.name();
    }

    static String structuredMessage(
            UUID npcUuid, String operation, String reason, Location current, Location target,
            Location citizensTarget, double distanceMargin, double pathMargin, String pathfinderType,
            float range, int stationaryTicks, String strategy, String pathState, String examiners,
            long elapsedTicks) {
        double deltaX = current == null || target == null ? Double.NaN : current.getX() - target.getX();
        double deltaY = current == null || target == null ? Double.NaN : current.getY() - target.getY();
        double deltaZ = current == null || target == null ? Double.NaN : current.getZ() - target.getZ();
        double horizontal = Math.hypot(deltaX, deltaZ);
        return "NPC_NAV_END uuid=" + npcUuid
                + " operation=" + operation
                + " reason=" + reason
                + " current=" + position(current)
                + " target=" + position(target)
                + " citizensTarget=" + position(citizensTarget)
                + " deltaX=" + number(deltaX)
                + " deltaY=" + number(deltaY)
                + " deltaZ=" + number(deltaZ)
                + " horizontal=" + number(horizontal)
                + " distanceMargin=" + number(distanceMargin)
                + " pathMargin=" + number(pathMargin)
                + " pathfinder=" + pathfinderType
                + " range=" + number(range)
                + " stationaryTicks=" + stationaryTicks
                + " strategy=" + strategy
                + " path=" + pathState
                + " examiners=" + examiners
                + " elapsedTicks=" + elapsedTicks
                + (stalledReason(reason) ? stalledGeometry(current, target) : "");
    }

    private static boolean stalledReason(String reason) {
        return "PLUGIN".equals(reason) || "STUCK".equals(reason);
    }

    static String stalledGeometry(Location current, Location target) {
        if (current == null || target == null || current.getWorld() == null
                || !current.getWorld().equals(target.getWorld())) return " geometry=unavailable";
        Block feet = current.getBlock();
        if (feet == null) return " geometry=unavailable";
        Block head = feet.getRelative(0, 1, 0);
        Block support = feet.getRelative(0, -1, 0);
        if (head == null || support == null) return " geometry=unavailable";
        double deltaX = target.getX() - current.getX();
        double deltaY = target.getY() - current.getY();
        double deltaZ = target.getZ() - current.getZ();
        double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        double directionX = length == 0.0 ? 0.0 : deltaX / length;
        double directionY = length == 0.0 ? 0.0 : deltaY / length;
        double directionZ = length == 0.0 ? 0.0 : deltaZ / length;
        return " feetBlock=" + feet.getX() + "," + feet.getY() + "," + feet.getZ()
                + " feet=" + feet.getType()
                + " head=" + head.getType()
                + " support=" + support.getType()
                + " local=" + number(current.getX() - current.getBlockX()) + ","
                + number(current.getY() - current.getBlockY()) + ","
                + number(current.getZ() - current.getBlockZ())
                + " yaw=" + number(current.getYaw())
                + " pitch=" + number(current.getPitch())
                + " targetBlock=" + target.getBlockX() + "," + target.getBlockY() + "," + target.getBlockZ()
                + " targetDirection=" + number(directionX) + "," + number(directionY) + "," + number(directionZ);
    }

    private void log(
            NPC npc, String operation, String reason, Location target,
            double distanceMargin, double pathMargin, NavigatorParameters parameters, long elapsedTicks) {
        long tick = Bukkit.getCurrentTick();
        LogKey key = new LogKey(npc.getUniqueId(), operation, reason);
        if (tick < nextLogTick.getOrDefault(key, Long.MIN_VALUE)) return;
        nextLogTick.put(key, tick + LOG_INTERVAL_TICKS);
        Location current = npc.isSpawned() ? npc.getEntity().getLocation() : null;
        Navigator navigator = npc.getNavigator();
        Location citizensTarget = navigator.getTargetAsLocation();
        PathStrategy pathStrategy = navigator.getPathStrategy();
        logger.info(structuredMessage(
                npc.getUniqueId(), operation, reason, current, target, citizensTarget,
                distanceMargin, pathMargin, parameters.pathfinderType().name(), parameters.range(),
                parameters.stationaryTicks(), strategyName(pathStrategy), pathState(pathStrategy),
                examinerNames(parameters), elapsedTicks));
    }

    private static String strategyName(PathStrategy strategy) {
        return strategy == null ? "none" : strategy.getClass().getSimpleName();
    }

    static String pathState(PathStrategy strategy) {
        if (strategy == null) return "none";
        try {
            Iterable<org.bukkit.util.Vector> path = strategy.getPath();
            if (path == null) return "absent";
            return path.iterator().hasNext() ? "present" : "empty";
        } catch (RuntimeException exception) {
            return "unavailable";
        }
    }

    private static String examinerNames(NavigatorParameters parameters) {
        return Arrays.stream(parameters.examiners())
                .map(examiner -> examiner.getClass().getSimpleName())
                .sorted()
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String position(Location location) {
        if (location == null || location.getWorld() == null) return "unavailable";
        return location.getWorld().getName() + ":" + number(location.getX()) + ","
                + number(location.getY()) + "," + number(location.getZ());
    }

    private static String number(double value) {
        return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.4f", value) : "unavailable";
    }

    private record LogKey(UUID npcUuid, String operation, String reason) {
    }
}
