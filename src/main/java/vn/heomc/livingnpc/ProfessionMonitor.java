package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;

final class ProfessionMonitor {
    private static final long STALL_TICKS = 200L;
    private static final long NAVIGATION_START_GRACE_TICKS = 20L;
    private static final long HEALTH_REPORT_INTERVAL_TICKS = 1200L;
    private static final double MOVEMENT_EPSILON_SQUARED = 0.0625;

    private final FarmerManager residents;
    private final VillageStore villages;
    private final RancherManager ranchers;
    private final FisherManager fishers;
    private final CivilProfessionManager civilProfessions;
    private final MerchantManager merchants;
    private final Logger logger;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, ProfessionDiagnostic> diagnostics = new HashMap<>();
    private long nextHealthReportTick = HEALTH_REPORT_INTERVAL_TICKS;

    ProfessionMonitor(
            FarmerManager residents, VillageStore villages, RancherManager ranchers,
            FisherManager fishers, CivilProfessionManager civilProfessions,
            MerchantManager merchants, Logger logger) {
        this.residents = residents;
        this.villages = villages;
        this.ranchers = ranchers;
        this.fishers = fishers;
        this.civilProfessions = civilProfessions;
        this.merchants = merchants;
        this.logger = logger;
    }

    void tick(long serverTick, LivingNpcConfig config) {
        java.util.Set<UUID> current = new java.util.HashSet<>();
        for (FarmerDefinition definition : residents.definitions()) {
            current.add(definition.npcUuid());
            NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(definition.npcUuid());
            ProfessionDiagnostic diagnostic = inspect(definition, npc, serverTick, config);
            ProfessionDiagnostic previous = diagnostics.put(definition.npcUuid(), diagnostic);
            if (previous == null || previous.level() != diagnostic.level()
                    || !previous.message().equals(diagnostic.message())) {
                reportTransition(definition, previous, diagnostic);
            }
        }
        states.keySet().removeIf(uuid -> !current.contains(uuid));
        diagnostics.keySet().removeIf(uuid -> !current.contains(uuid));
        if (serverTick >= nextHealthReportTick) {
            reportHealth();
            reportActivities();
            nextHealthReportTick = serverTick + HEALTH_REPORT_INTERVAL_TICKS;
        }
    }

    ProfessionDiagnostic diagnostic(UUID npcUuid) {
        return diagnostics.getOrDefault(
                npcUuid, new ProfessionDiagnostic(ProfessionDiagnostic.Level.WAITING, "Chưa có dữ liệu theo dõi"));
    }

    private ProfessionDiagnostic inspect(
            FarmerDefinition definition, NPC npc, long serverTick, LivingNpcConfig config) {
        State state = states.computeIfAbsent(definition.npcUuid(), ignored -> new State(serverTick));
        if (npc == null || !npc.isSpawned()) return waiting(state, serverTick, "NPC chưa spawn");
        if (!definition.enabled(BehaviorFlag.MASTER)) return waiting(state, serverTick, "NPC hoạt động đang TẮT");
        FarmerPhase phase = phase(definition);
        Location current = npc.getEntity().getLocation();
        traceAction(definition, npc, state, phase, current, serverTick);
        if (phase == FarmerPhase.GOING_TO_BED || phase == FarmerPhase.SLEEPING) {
            return inspectPhase(state, phase, current, npc, serverTick, config.navigationTimeoutTicks());
        }
        String sleep = residents.sleepDebug(definition.npcUuid());
        if (sleepFailure(sleep)) {
            return error(state, current, "Không thể ngủ: " + sleep);
        }
        if (FarmerRuntime.isBedtime(current.getWorld().getTime()) && "RETRY_COOLDOWN".equals(sleep)) {
            return waiting(state, serverTick, "Đang chờ thử lại đường tới giường");
        }
        String blocked = blockedReason(definition, npc, config);
        if (blocked != null) return waiting(state, serverTick, "Chưa đủ điều kiện: " + blocked);

        return inspectPhase(state, phase, current, npc, serverTick, config.navigationTimeoutTicks());
    }

    private ProfessionDiagnostic inspectPhase(
            State state, FarmerPhase phase, Location current, NPC npc, long serverTick,
            long navigationTimeoutTicks) {
        if (phase != state.lastPhase) {
            state.lastPhase = phase;
            state.phaseStartedTick = serverTick;
            state.lastProgressTick = serverTick;
            state.lastLocation = current.clone();
        }
        if (phase == null || phase == FarmerPhase.INACTIVE) {
            updateProgress(state, current, serverTick);
            return new ProfessionDiagnostic(ProfessionDiagnostic.Level.WAITING, "Đủ điều kiện, đang chờ việc");
        }
        if (!isMoving(phase)) {
            updateProgress(state, current, serverTick);
            return new ProfessionDiagnostic(ProfessionDiagnostic.Level.OK, "Runtime bình thường: " + phase);
        }
        if (moved(state.lastLocation, current)) updateProgress(state, current, serverTick);
        if (!npc.getNavigator().isNavigating()
                && navigationGraceExpired(state.phaseStartedTick, serverTick)) {
            return error(state, current, "Phase " + phase + " nhưng Citizens navigator không chạy");
        }
        long stallTicks = Math.max(STALL_TICKS, navigationTimeoutTicks);
        if (serverTick - state.lastProgressTick >= stallTicks) {
            return error(state, current, "Bị kẹt ở phase " + phase + ", không tiến triển trong "
                    + Math.max(1L, stallTicks / 20L) + " giây");
        }
        return new ProfessionDiagnostic(ProfessionDiagnostic.Level.OK, "Đang di chuyển: " + phase);
    }

    static boolean navigationGraceExpired(long phaseStartedTick, long serverTick) {
        return serverTick - phaseStartedTick >= NAVIGATION_START_GRACE_TICKS;
    }

    static boolean isMoving(FarmerPhase phase) {
        return phase == FarmerPhase.GOING_HOME || phase == FarmerPhase.GOING_TO_BED
                || phase == FarmerPhase.GOING_TO_PLOT || phase == FarmerPhase.GOING_TO_CROP
                || phase == FarmerPhase.GOING_TO_STORAGE || phase == FarmerPhase.RETURNING_TO_PLOT
                || phase == FarmerPhase.GOING_TO_FISHING_SPOT || phase == FarmerPhase.GOING_TO_WORK_STATION
                || phase == FarmerPhase.GOING_TO_MARKET || phase == FarmerPhase.GOING_TO_SCENIC
                || phase == FarmerPhase.GOING_TO_SEAT
                || phase == FarmerPhase.PATROLLING || phase == FarmerPhase.GOING_TO_STALL
                || phase == FarmerPhase.SHELTERING;
    }

    static boolean sleepFailure(String sleepDebug) {
        return "BED_NOT_FOUND_OR_OCCUPIED".equals(sleepDebug)
                || "NO_SAFE_BED_STANDING_BLOCK".equals(sleepDebug)
                || "BED_PATH_UNREACHABLE".equals(sleepDebug)
                || "BED_NAVIGATION_FAILED".equals(sleepDebug)
                || "SLEEP_REJECTED".equals(sleepDebug)
                || "HOME_UNRESOLVED".equals(sleepDebug)
                || "HOME_DIFFERENT_WORLD".equals(sleepDebug);
    }

    private FarmerPhase phase(FarmerDefinition definition) {
        FarmerPhase shared = residents.phase(definition.npcUuid());
        if (shared == FarmerPhase.GOING_TO_BED || shared == FarmerPhase.SLEEPING) return shared;
        return switch (definition.activeRole()) {
            case FISHER -> fishers.phase(definition.npcUuid());
            case RANCHER -> ranchers.phase(definition.npcUuid());
            case COOK, CRAFTER, MINER, SECURITY -> civilProfessions.phase(definition.npcUuid());
            case MERCHANT -> merchants.phase(definition.npcUuid());
            default -> shared;
        };
    }

    private String blockedReason(FarmerDefinition definition, NPC npc, LivingNpcConfig config) {
        if (!ReleasePolicy.roleEnabled(definition.activeRole())) {
            return "nghề bị khóa trong Season " + ReleasePolicy.SEASON;
        }
        if (definition.activeRole() == ResidentRole.RESIDENT) return null;
        VillageDefinition village = villages.get(definition.villageId());
        if (village == null) return "chưa thuộc làng hợp lệ";
        Location center;
        if (definition.activeRole() == ResidentRole.FARMER) {
            center = definition.plot() == null ? null : definition.plot().resolve();
            if (center == null) return "chưa gán khu ruộng";
            if (villages.deliveryChest(village.id()) == null) return "làng chưa gán rương kho";
            if (!definition.enabled(BehaviorFlag.HARVEST) || !definition.enabled(BehaviorFlag.PLANT)) {
                return "Làm nông đang TẮT";
            }
        } else if (definition.activeRole() == ResidentRole.MERCHANT) {
            MerchantStall stall = village.merchantStall(definition.npcUuid());
            center = stall == null || !stall.complete() ? null : stall.sellerPoint().resolve();
            if (center == null) return "quầy Dân buôn chưa đủ hai điểm";
        } else {
            VillageWorkZoneType zone = definition.activeRole() == ResidentRole.FISHER
                    ? VillageWorkZoneType.FISHING
                    : definition.activeRole() == ResidentRole.RANCHER
                            ? VillageWorkZoneType.RANCH : CivilProfessionRuntime.zoneFor(definition.activeRole());
            if (zone == null) return "runtime nghề chưa được triển khai";
            StoredLocation stored = village.workZone(zone);
            center = stored == null ? null : stored.resolve();
            if (center == null) return "chưa đặt " + zone.storageKey();
        }
        if (!npc.getEntity().getWorld().equals(center.getWorld())) return "NPC và khu làm việc khác world";
        if (center.getWorld().getNearbyPlayers(center, config.activationRange()).isEmpty()
                && npc.getEntity().getWorld().getNearbyPlayers(
                        npc.getEntity().getLocation(), config.activationRange()).isEmpty()) {
            return "không có người chơi gần NPC hoặc khu làm việc";
        }
        ResidentSchedule schedule = definition.schedule(
                definition.activeRole(), new ResidentSchedule(config.workStartTick(), config.workEndTick()));
        if (definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                && !SchedulePolicy.isScheduledTime(center.getWorld().getTime(), schedule)) return "ngoài ca làm việc";
        if (definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE) && center.getWorld().hasStorm()) return "trời đang mưa";
        return null;
    }

    private boolean moved(Location previous, Location current) {
        return previous == null || !previous.getWorld().equals(current.getWorld())
                || previous.distanceSquared(current) >= MOVEMENT_EPSILON_SQUARED;
    }

    private void traceAction(
            FarmerDefinition definition, NPC npc, State state, FarmerPhase phase,
            Location current, long serverTick) {
        boolean navigating = npc.getNavigator().isNavigating();
        Location target = npc.getNavigator().getTargetAsLocation();
        String targetKey = locationKey(target);
        if (phase == state.tracedPhase && navigating == state.tracedNavigating
                && targetKey.equals(state.tracedTarget)) return;

        logger.info("NPC_ACTION uuid=" + definition.npcUuid() + " name=\""
                + definition.profile().name() + "\" role=" + definition.activeRole().storageKey()
                + " tick=" + serverTick + " phase=" + state.tracedPhase + "->" + phase
                + " navigation=" + state.tracedNavigating + "->" + navigating
                + " pos=" + locationKey(current) + " target=" + targetKey
                + " targetDistance=" + distance(current, target));
        state.tracedPhase = phase;
        state.tracedNavigating = navigating;
        state.tracedTarget = targetKey;
    }

    private static String locationKey(Location location) {
        if (location == null || location.getWorld() == null) return "none";
        return location.getWorld().getName() + ":" + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }

    private static String distance(Location from, Location to) {
        if (to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return "unavailable";
        return String.format(Locale.ROOT, "%.2f", Math.sqrt(from.distanceSquared(to)));
    }

    private ProfessionDiagnostic waiting(State state, long serverTick, String message) {
        state.lastLocation = null;
        state.lastProgressTick = serverTick;
        return new ProfessionDiagnostic(ProfessionDiagnostic.Level.WAITING, message);
    }

    private ProfessionDiagnostic error(State state, Location current, String message) {
        state.lastLocation = current.clone();
        return new ProfessionDiagnostic(ProfessionDiagnostic.Level.ERROR, message);
    }

    private void updateProgress(State state, Location current, long serverTick) {
        state.lastLocation = current.clone();
        state.lastProgressTick = serverTick;
    }

    private void reportTransition(
            FarmerDefinition definition, ProfessionDiagnostic previous, ProfessionDiagnostic current) {
        String prefix = "Theo dõi nghề " + definition.profile().name() + " [" + definition.npcUuid() + "]: ";
        if (current.level() == ProfessionDiagnostic.Level.ERROR) {
            logger.warning("NPC_DIAGNOSTIC uuid=" + definition.npcUuid() + " state=ERROR "
                    + prefix + current.message());
        }
        else if (previous != null && previous.level() == ProfessionDiagnostic.Level.ERROR) {
            logger.info("NPC_DIAGNOSTIC uuid=" + definition.npcUuid() + " state=RECOVERED "
                    + prefix + "đã phục hồi - " + current.message());
        }
    }

    private void reportHealth() {
        long ok = diagnostics.values().stream()
                .filter(diagnostic -> diagnostic.level() == ProfessionDiagnostic.Level.OK).count();
        long waiting = diagnostics.values().stream()
                .filter(diagnostic -> diagnostic.level() == ProfessionDiagnostic.Level.WAITING).count();
        long errors = diagnostics.values().stream()
                .filter(diagnostic -> diagnostic.level() == ProfessionDiagnostic.Level.ERROR).count();
        logger.info("NPC_HEALTH total=" + diagnostics.size() + " ok=" + ok
                + " waiting=" + waiting + " errors=" + errors);
    }

    private void reportActivities() {
        for (FarmerDefinition definition : residents.definitions()) {
            NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(definition.npcUuid());
            ProfessionDiagnostic diagnostic = diagnostic(definition.npcUuid());
            if (npc == null || !npc.isSpawned()) {
                logger.info("NPC_ACTIVITY uuid=" + definition.npcUuid() + " name=\""
                        + definition.profile().name() + "\" role=" + definition.activeRole().storageKey()
                        + " spawned=false sleep=" + residents.sleepDebug(definition.npcUuid())
                        + " diagnostic=\"" + diagnostic.message() + "\"");
                continue;
            }
            Location current = npc.getEntity().getLocation();
            Location home = definition.home().resolve();
            String homeDistance = home == null || !home.getWorld().equals(current.getWorld())
                    ? "unavailable"
                    : String.format(Locale.ROOT, "%.1f", Math.sqrt(home.distanceSquared(current)));
            logger.info("NPC_ACTIVITY uuid=" + definition.npcUuid() + " name=\""
                    + definition.profile().name() + "\" role=" + definition.activeRole().storageKey()
                    + " world=" + current.getWorld().getName() + " worldTime=" + current.getWorld().getTime()
                    + " phase=" + phase(definition) + " sleep=" + residents.sleepDebug(definition.npcUuid())
                    + " pos=" + current.getBlockX() + "," + current.getBlockY() + "," + current.getBlockZ()
                    + " homeDistance=" + homeDistance + " navigating=" + npc.getNavigator().isNavigating()
                    + " diagnostic=\"" + diagnostic.message() + "\"");
        }
    }

    private static final class State {
        private Location lastLocation;
        private long lastProgressTick;
        private FarmerPhase lastPhase;
        private long phaseStartedTick;
        private FarmerPhase tracedPhase;
        private boolean tracedNavigating;
        private String tracedTarget = "none";

        private State(long serverTick) {
            lastProgressTick = serverTick;
            phaseStartedTick = serverTick;
        }
    }
}
