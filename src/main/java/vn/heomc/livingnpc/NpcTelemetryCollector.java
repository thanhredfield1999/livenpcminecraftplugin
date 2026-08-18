package vn.heomc.livingnpc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.PathStrategy;
import org.bukkit.Location;
import org.bukkit.block.Block;

final class NpcTelemetryCollector {
    static final int FRONT_PROBE_STEPS = 3;
    private static final int MAX_IDENTITIES = 512;
    private final NpcTelemetryBuffer buffer;
    private final NpcEconomy economy;
    private final Map<java.util.UUID, NpcIdentity> identities = new LinkedHashMap<>();

    NpcTelemetryCollector(NpcTelemetryBuffer buffer) {
        this(buffer, null);
    }

    NpcTelemetryCollector(NpcTelemetryBuffer buffer, NpcEconomy economy) {
        this.buffer = buffer;
        this.economy = economy;
    }

    void recordAction(
            FarmerDefinition definition, VillageDefinition village, String npcName, FarmerPhase phase,
            Location current, Location target, boolean navigating, String strategy, String path,
            long serverTick) {
        recordAction(definition, village, npcName, phase, current, target, navigating, strategy, path, serverTick, null);
    }

    void recordAction(
            FarmerDefinition definition, VillageDefinition village, String npcName, FarmerPhase phase,
            Location current, Location target, boolean navigating, String strategy, String path,
            long serverTick, String skinName) {
        if (definition == null || current == null || current.getWorld() == null) return;
        String name = npcName == null ? definition.profile().name() : npcName;
        String role = definition.activeRole().storageKey();
        rememberIdentity(definition.npcUuid(), name, role);
        NpcTelemetryPosition npcPosition = NpcTelemetryPosition.from(current);
        NpcTelemetryPosition targetPosition = NpcTelemetryPosition.from(target);
        NpcTelemetryBlockProbe obstacle = firstObstacle(blockProbes(current));
        NpcTelemetrySemanticPoint semanticPoint = semanticPoint(definition, village, phase, target);
        NpcTelemetryNavigation navigation = new NpcTelemetryNavigation(
                navigating,
                target == null || target.getWorld() == null ? null : target.getWorld().getName(),
                targetPosition,
                strategy,
                path,
                "unavailable",
                "unavailable",
                0.0f,
                -1,
                Double.NaN,
                Double.NaN,
                "ACTIVE",
                0L);
        buffer.record(new NpcTelemetryEvent(
                1,
                "ACTION",
                definition.npcUuid(),
                name,
                role,
                definition.villageId(),
                current.getWorld().getName(),
                npcPosition,
                targetPosition,
                phase == null ? "UNKNOWN" : phase.name(),
                phase == null ? "UNKNOWN" : phase.name(),
                navigation,
                path,
                obstacle,
                semanticPoint,
                blockProbes(current),
                serverTick,
                System.currentTimeMillis(),
                safeSkinName(skinName),
                account(definition.npcUuid())));
    }

    void recordNavigationEnd(
            java.util.UUID npcUuid, String npcName, String role, String operation, String reason,
            Location current, Location target, Location citizensTarget, double distanceMargin,
            double pathMargin, NavigatorParameters parameters, PathStrategy pathStrategy, long elapsedTicks) {
        Location source = current != null ? current : target;
        if (npcUuid == null || source == null || source.getWorld() == null) return;
        NpcIdentity identity = identities.get(npcUuid);
        if (identity != null) {
            npcName = identity.name();
            role = identity.role();
        }
        String path = NavigationDiagnostics.pathState(pathStrategy);
        NpcTelemetryNavigation navigation = new NpcTelemetryNavigation(
                false,
                citizensTarget == null || citizensTarget.getWorld() == null ? null : citizensTarget.getWorld().getName(),
                NpcTelemetryPosition.from(citizensTarget),
                pathStrategy == null ? "none" : pathStrategy.getClass().getSimpleName(),
                path,
                parameters == null ? "[]" : NavigationDiagnostics.examinerNames(parameters),
                parameters == null ? "unavailable" : parameters.pathfinderType().name(),
                parameters == null ? 0.0f : parameters.range(),
                parameters == null ? -1 : parameters.stationaryTicks(),
                distanceMargin,
                pathMargin,
                reason,
                elapsedTicks);
        List<NpcTelemetryBlockProbe> probes = blockProbes(current);
        buffer.record(new NpcTelemetryEvent(
                1,
                "NAVIGATION_END",
                npcUuid,
                npcName == null ? "" : npcName,
                role == null ? "unknown" : role,
                source.getWorld().getName(),
                NpcTelemetryPosition.from(current),
                NpcTelemetryPosition.from(target),
                operation,
                operation,
                navigation,
                path,
                firstObstacle(probes),
                null,
                probes,
                currentTick(),
                System.currentTimeMillis(),
                null,
                account(npcUuid)));
    }

    NpcTelemetrySnapshot snapshot() {
        return buffer.snapshot();
    }

    private NpcTelemetryAccount account(java.util.UUID npcUuid) {
        if (economy == null || npcUuid == null) return null;
        return economy.telemetryAccounts().stream()
                .filter(account -> npcUuid.equals(account.npcUuid()))
                .findFirst()
                .map(NpcTelemetryAccount::from)
                .orElse(null);
    }

    private void rememberIdentity(java.util.UUID npcUuid, String name, String role) {
        if (npcUuid == null) return;
        identities.put(npcUuid, new NpcIdentity(name, role));
        while (identities.size() > MAX_IDENTITIES) identities.remove(identities.keySet().iterator().next());
    }

    private record NpcIdentity(String name, String role) {
    }

    private static long currentTick() {
        try {
            return org.bukkit.Bukkit.getCurrentTick();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static NpcTelemetrySemanticPoint semanticPoint(
            FarmerDefinition definition, VillageDefinition village, FarmerPhase phase, Location target) {
        if (target == null || target.getWorld() == null) return null;
        if (phase == FarmerPhase.GOING_TO_BED || phase == FarmerPhase.GOING_HOME || phase == FarmerPhase.SLEEPING) {
            return point("HOME", "bed", definition.home(), target);
        }
        if (phase == FarmerPhase.GOING_TO_PLOT || phase == FarmerPhase.RETURNING_TO_PLOT
                || phase == FarmerPhase.GOING_TO_CROP || phase == FarmerPhase.WORKING) {
            return point("PLOT", "plot", definition.plot(), target);
        }
        if (village != null && definition.activeRole() == ResidentRole.FISHER) {
            return point("WORK_ZONE", "fishing", village.workZone(VillageWorkZoneType.FISHING), target);
        }
        if (village != null && definition.activeRole() == ResidentRole.RANCHER) {
            return point("WORK_ZONE", "ranch", village.workZone(VillageWorkZoneType.RANCH), target);
        }
        VillageWorkZoneType zone = CivilProfessionRuntime.zoneFor(definition.activeRole());
        if (village != null && zone != null) return point("WORK_ZONE", zone.storageKey(), village.workZone(zone), target);
        return new NpcTelemetrySemanticPoint("TARGET", "navigation-target", target.getWorld().getName(), NpcTelemetryPosition.from(target));
    }

    private static NpcTelemetrySemanticPoint point(
            String type, String name, StoredLocation stored, Location fallback) {
        if (stored != null) {
            NpcTelemetryPosition position = new NpcTelemetryPosition(
                    stored.world(), (int) Math.floor(stored.x()), (int) Math.floor(stored.y()),
                    (int) Math.floor(stored.z()), stored.x(), stored.y(), stored.z(),
                    stored.yaw(), stored.pitch());
            return new NpcTelemetrySemanticPoint(type, name, stored.world(), position);
        }
        if (fallback == null || fallback.getWorld() == null) return null;
        return new NpcTelemetrySemanticPoint(type, name, fallback.getWorld().getName(), NpcTelemetryPosition.from(fallback));
    }

    private static List<NpcTelemetryBlockProbe> blockProbes(Location current) {
        if (current == null || current.getWorld() == null) return List.of();
        List<NpcTelemetryBlockProbe> probes = new ArrayList<>();
        Block feet = current.getBlock();
        probes.add(NpcTelemetryBlockProbe.from("feet", feet, RuntimeChunkAvailability.loaded(current)));
        probes.add(NpcTelemetryBlockProbe.from("head", feet.getRelative(0, 1, 0), RuntimeChunkAvailability.loaded(current)));
        probes.add(NpcTelemetryBlockProbe.from("support", feet.getRelative(0, -1, 0), RuntimeChunkAvailability.loaded(current)));
        int stepX = Integer.compare(Math.round(current.getDirection().getBlockX()), 0);
        int stepZ = Integer.compare(Math.round(current.getDirection().getBlockZ()), 0);
        if (stepX == 0 && stepZ == 0) stepZ = 1;
        for (int step = 1; step <= FRONT_PROBE_STEPS; step++) {
            Block front = feet.getRelative(stepX * step, 0, stepZ * step);
            Location frontLocation = front.getLocation();
            boolean loaded = RuntimeChunkAvailability.loaded(frontLocation);
            probes.add(NpcTelemetryBlockProbe.from("front-" + step, front, loaded));
            probes.add(NpcTelemetryBlockProbe.from("front-" + step + "-support", front.getRelative(0, -1, 0), loaded));
        }
        return probes.stream().filter(java.util.Objects::nonNull).toList();
    }

    private static NpcTelemetryBlockProbe firstObstacle(List<NpcTelemetryBlockProbe> probes) {
        return probes.stream()
                .filter(NpcTelemetryBlockProbe::obstacle)
                .min(Comparator.comparing(NpcTelemetryBlockProbe::relation))
                .orElse(null);
    }

    private static String safeSkinName(String skinName) {
        return skinName != null && skinName.matches("[A-Za-z0-9_]{1,16}") ? skinName : null;
    }
}
