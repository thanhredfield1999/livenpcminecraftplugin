package vn.heomc.livingnpc;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

final class RancherRuntime {
    private final NPC npc;
    private final NpcEconomy economy;
    private final VillageStore villages;
    private final RanchWorkCoordinator workCoordinator;
    private final java.util.function.Consumer<Animals> culler;
    private final java.util.function.LongConsumer experienceAwarder;
    private FarmerDefinition definition;
    private long nextActionTick;
    private UUID navigationTarget;
    private long navigationStartedTick;
    private String navigationFailure;
    private GateRouteCoordinator gateRouteCoordinator;
    private List<Animals> feedingAnimals = List.of();
    private int feedingIndex;
    private String feedingFood;
    private Location foodChest;
    private boolean fetchingFood;
    private final java.util.Set<StoredLocation> failedDeliveryLocations = new java.util.HashSet<>();
    private Location carriedDeliveryChest;
    private final Map<StoredLocation, java.util.Set<UUID>> knownHerdByPen = new java.util.HashMap<>();
    private Animals escapedAnimal;
    private Location returnTarget;
    private Location patrolTarget;
    private Location workEntryTarget;
    private StoredLocation activePen;
    private int nextPenIndex;
    private StoredLocation validatedPen;
    private long validationExpiresTick;
    private boolean penValid;

    RancherRuntime(
            NPC npc, FarmerDefinition definition, NpcEconomy economy, VillageStore villages,
            RanchWorkCoordinator workCoordinator,
            java.util.function.Consumer<Animals> culler,
            java.util.function.LongConsumer experienceAwarder) {
        this.npc = npc;
        this.definition = definition;
        this.economy = economy;
        this.villages = villages;
        this.workCoordinator = workCoordinator;
        this.culler = culler;
        this.experienceAwarder = experienceAwarder;
    }

    void updateDefinition(FarmerDefinition updated) {
        boolean wasRancher = ownsRole(definition.activeRole());
        boolean villageChanged = !java.util.Objects.equals(definition.villageId(), updated.villageId());
        if (villageChanged) {
            releaseWorkState();
            knownHerdByPen.clear();
        }
        definition = updated;
        if (wasRancher && !ownsRole(updated.activeRole())) suspend();
    }

    static boolean ownsRole(ResidentRole role) {
        return role == ResidentRole.RANCHER;
    }

    void tick(long serverTick, LivingNpcConfig config) {
        if (!npc.isSpawned() || definition.activeRole() != ResidentRole.RANCHER
                || !definition.enabled(BehaviorFlag.MASTER)) {
            suspend();
            return;
        }
        VillageDefinition village = villages.get(definition.villageId());
        if (activePen == null && serverTick < nextActionTick) return;
        StoredLocation storedZone = activePen != null ? activePen
                : village == null || village.ranchPens().isEmpty() ? null : selectPen(village, config);
        Location zone = storedZone == null ? null : storedZone.resolve();
        if (zone == null || !npc.getEntity().getWorld().equals(zone.getWorld())
                || !RuntimeChunkAvailability.loaded(npc.getEntity().getLocation())
                || !RuntimeChunkAvailability.loadedArea(zone, config.workZoneValidationRadius())) {
            suspend();
            return;
        }
        ResidentSchedule schedule = definition.schedule(
                ResidentRole.RANCHER, new ResidentSchedule(config.workStartTick(), config.workEndTick()));
        if (definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                && !SchedulePolicy.isWorkTime(zone.getWorld().getTime(), zone.getWorld().hasStorm(), schedule)) {
            suspend();
            return;
        }
        if (!validPen(storedZone, zone, serverTick, config)) {
            suspend();
            return;
        }
        if (!feedingAnimals.isEmpty()) {
            if (fetchingFood) {
                continueFetchingFood(serverTick, config);
                return;
            }
            continueFeeding(serverTick, config);
            return;
        }
        if (escapedAnimal != null) {
            continueReturningEscaped(zone, village.id(), serverTick, config);
            return;
        }
        if (economy.carriedInventoryFull(npc.getUniqueId())
                && depositCarriedLoot(serverTick, config, village.id())) return;
        activePen = storedZone;
        if (!enterPen(zone, serverTick, config)) return;
        if (serverTick < nextActionTick) return;
        if (!workCoordinator.acquire(
                village.id(), npc.getUniqueId(), storedZone, config.workZoneValidationRadius())) {
            nextActionTick = serverTick + config.rancher().scanIntervalTicks();
            return;
        }
        int radius = config.workZoneValidationRadius();
        List<Animals> animals = zone.getWorld().getNearbyEntitiesByType(
                Animals.class, zone, radius, config.workZoneValidationVerticalRange(), radius,
                animal -> supported(animal) && inside(animal.getLocation(), zone, radius,
                        config.workZoneValidationVerticalRange())).stream().toList();
        java.util.Set<UUID> knownHerd = knownHerdByPen.computeIfAbsent(
                storedZone, ignored -> new java.util.HashSet<>());
        animals.forEach(animal -> knownHerd.add(animal.getUniqueId()));
        if (tryCollectProduct(zone, village, serverTick, config)) return;
        if (tryReturnEscaped(zone, village.id(), serverTick, config)) return;
        if (tryCull(animals, village, serverTick, config)) return;
        if (tryBreed(animals, village, serverTick, config)) return;
        if (tryPatrol(zone, serverTick, config)) return;
        releasePen();
    }

    private boolean validPen(
            StoredLocation storedZone, Location zone, long serverTick, LivingNpcConfig config) {
        if (!storedZone.equals(validatedPen) || serverTick >= validationExpiresTick) {
            validatedPen = storedZone;
            penValid = WorkZoneValidator.validate(
                    zone, VillageWorkZoneType.RANCH,
                    config.workZoneValidationRadius(), config.workZoneValidationVerticalRange()).valid();
            validationExpiresTick = serverTick + 200L;
        }
        return penValid;
    }

    void suspend() {
        if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
        releaseWorkState();
    }

    void releaseWorkState() {
        if (gateRouteCoordinator != null) {
            gateRouteCoordinator.cancel();
            gateRouteCoordinator = null;
        }
        releasePen();
        navigationTarget = null;
        feedingAnimals = List.of();
        feedingIndex = 0;
        feedingFood = null;
        foodChest = null;
        fetchingFood = false;
        failedDeliveryLocations.clear();
        carriedDeliveryChest = null;
        releaseEscapedAnimal();
        patrolTarget = null;
        workEntryTarget = null;
        activePen = null;
        setHand(null);
    }

    FarmerPhase phase() {
        return (workEntryTarget != null || navigationTarget != null) && npc.getNavigator().isNavigating()
                ? FarmerPhase.GOING_TO_WORK_STATION : FarmerPhase.RESTING;
    }

    boolean taskActive() {
        return workEntryTarget != null || navigationTarget != null || !feedingAnimals.isEmpty() || escapedAnimal != null;
    }

    String status(LivingNpcConfig config) {
        if (!npc.isSpawned()) return "NPC chưa spawn";
        if (!definition.enabled(BehaviorFlag.MASTER)) return "NPC hoạt động đang TẮT";
        VillageDefinition village = villages.get(definition.villageId());
        StoredLocation storedZone = village == null ? null : village.workZone(VillageWorkZoneType.RANCH);
        Location zone = storedZone == null ? null : storedZone.resolve();
        if (zone == null) return "Chưa đặt Khu chăn nuôi";
        if (!npc.getEntity().getWorld().equals(zone.getWorld())) return "NPC và Khu chăn nuôi khác world";
        if (!RuntimeChunkAvailability.loaded(npc.getEntity().getLocation())
                || !RuntimeChunkAvailability.loadedArea(zone, config.workZoneValidationRadius())) {
            return "World hoặc chunk của NPC/Khu chăn nuôi chưa load";
        }
        ResidentSchedule schedule = definition.schedule(
                ResidentRole.RANCHER, new ResidentSchedule(config.workStartTick(), config.workEndTick()));
        if (definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                && !SchedulePolicy.isScheduledTime(zone.getWorld().getTime(), schedule)) return "Ngoài ca chăn nuôi";
        if (zone.getWorld().hasStorm()) return "Trời đang mưa";
        WorkZoneValidation validation = WorkZoneValidator.validate(
                zone, VillageWorkZoneType.RANCH,
                config.workZoneValidationRadius(), config.workZoneValidationVerticalRange());
        if (!validation.valid()) return "Khu chăn nuôi thiếu " + validation.missing();
        if (workCoordinator.occupiedByOther(
                village.id(), npc.getUniqueId(), storedZone, config.workZoneValidationRadius())) {
            return "Đang chờ NPC Chăn nuôi khác hoàn thành lượt tại khu chung";
        }
        if (!feedingAnimals.isEmpty()) {
            if (navigationFailure != null) return navigationFailure;
            return feedingIndex == 0
                        ? "Đang cầm thức ăn và chuẩn bị cho vật nuôi thứ nhất ăn"
                        : feedingIndex < feedingAnimals.size()
                            ? "Đang đi tới và cho vật nuôi thứ hai ăn"
                            : "Đã cho hai con ăn; đang chờ chúng sinh sản";
        }
        int radius = config.workZoneValidationRadius();
        List<Animals> animals = zone.getWorld().getNearbyEntitiesByType(
                Animals.class, zone, radius, config.workZoneValidationVerticalRange(), radius,
                animal -> supported(animal) && inside(animal.getLocation(), zone, radius,
                        config.workZoneValidationVerticalRange())).stream().toList();
        for (Class<? extends Animals> species : supportedSpecies()) {
            List<Animals> group = animals.stream().filter(species::isInstance).toList();
            if (group.size() >= village.ranchAnimalLimit()) continue;
            long ready = group.stream().filter(Animals::isAdult)
                    .filter(Animals::canBreed).filter(animal -> !animal.isLoveMode()).count();
            if (ready < 2) continue;
            String food = foodKey(group.getFirst());
            int stored = economy.villageAccount(village.id()).quantity(food);
            return stored >= 2
                    ? "Sẵn sàng cho 2 " + speciesName(group.getFirst()) + " ăn bằng " + food
                    : "Kho cần 2 " + food + " cho " + speciesName(group.getFirst()) + "; hiện có " + stored;
        }
        if (navigationTarget != null && npc.getNavigator().isNavigating()) return "Đang đi tới vật nuôi";
        if (navigationFailure != null) return navigationFailure;
        if (escapedAnimal != null) return "Đang dắt " + speciesName(escapedAnimal) + " bị xổng về khu chăn nuôi";
        if (patrolTarget != null && npc.getNavigator().isNavigating()) return "Đang tuần tra và kiểm tra chuồng trại";
        return "Đang kiểm tra chuồng; chưa có cặp vật nuôi sẵn sàng sinh sản";
    }

    private boolean tryReturnEscaped(
            Location zone, String villageId, long serverTick, LivingNpcConfig config) {
        java.util.Set<UUID> knownHerd = knownHerdByPen.getOrDefault(activePen, java.util.Set.of());
        int search = config.rancher().escapeSearchRadius();
        int vertical = Math.max(config.workZoneValidationVerticalRange(), 4);
        escapedAnimal = zone.getWorld().getNearbyEntitiesByType(
                        Animals.class, zone, search, vertical, search,
                        animal -> supported(animal) && knownHerd.contains(animal.getUniqueId())
                                && (!animal.isLeashed() || npc.getEntity().equals(animal.getLeashHolder()))
                                && !inside(animal.getLocation(), zone, config.workZoneValidationRadius(),
                                        config.workZoneValidationVerticalRange()))
                .stream().min(Comparator.comparingDouble(animal ->
                        distanceOutsideSquared(animal.getLocation(), zone))).orElse(null);
        if (escapedAnimal == null) return false;
        navigationFailure = null;
        returnTarget = safeRanchTarget(zone, serverTick, config);
        if (returnTarget == null) {
            escapedAnimal = null;
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            return false;
        }
        continueReturningEscaped(zone, villageId, serverTick, config);
        return true;
    }

    private void continueReturningEscaped(
            Location zone, String villageId, long serverTick, LivingNpcConfig config) {
        Animals animal = escapedAnimal;
        java.util.Set<UUID> knownHerd = knownHerdByPen.getOrDefault(activePen, java.util.Set.of());
        if (animal == null || !animal.isValid() || !knownHerd.contains(animal.getUniqueId())
                || animal.getWorld() != zone.getWorld()) {
            finishEscapedReturn(serverTick, config, false);
            return;
        }
        if (inside(animal.getLocation(), zone, config.workZoneValidationRadius(),
                config.workZoneValidationVerticalRange())) {
            economy.recordActivity(npc.getUniqueId(), villageId, ResidentRole.RANCHER,
                    "Đưa vật nuôi xổng về chuồng", animal.getType().getKey().getKey(), 1);
            experienceAwarder.accept(2L);
            finishEscapedReturn(serverTick, config, true);
            return;
        }
        if (!animal.isLeashed() || !npc.getEntity().equals(animal.getLeashHolder())) {
            setHand(new ItemStack(foodMaterial(foodKey(animal))));
            if (!approach(animal, serverTick, config)) return;
            faceAnimal(animal);
            if (!animal.setLeashHolder(npc.getEntity())) {
                navigationFailure = "Không thể buộc dây dắt vật nuôi bị xổng";
                finishEscapedReturn(serverTick, config, false);
                return;
            }
            if (npc.getEntity() instanceof LivingEntity living) living.swingMainHand();
            navigationTarget = null;
        }
        if (returnTarget == null) returnTarget = safeRanchTarget(zone, serverTick, config);
        if (returnTarget == null) {
            finishEscapedReturn(serverTick, config, false);
            return;
        }
        if (animal.getUniqueId().equals(navigationTarget) && npc.getNavigator().isNavigating()
                && serverTick - navigationStartedTick < config.navigationTimeoutTicks()) return;
        if (animal.getUniqueId().equals(navigationTarget)
                && serverTick - navigationStartedTick >= config.navigationTimeoutTicks()) {
            navigationFailure = "Không thể dắt vật nuôi bị xổng về khu chăn nuôi";
            finishEscapedReturn(serverTick, config, false);
            return;
        }
        double margin = config.rancher().interactionRange();
        if (npc.getEntity().getLocation().distanceSquared(returnTarget) <= margin * margin) {
            returnTarget = safeRanchTarget(zone, serverTick + 1L, config);
        }
        navigateTo(returnTarget, animal.getUniqueId(), serverTick, config, margin, "RANCH_RETURN_ESCAPED");
    }

    private void finishEscapedReturn(long serverTick, LivingNpcConfig config, boolean success) {
        releaseEscapedAnimal();
        returnTarget = null;
        navigationTarget = null;
        setHand(null);
        releasePen();
        nextActionTick = serverTick + (success
                ? config.rancher().scanIntervalTicks() : config.navigationRetryBackoffTicks());
    }

    private void releaseEscapedAnimal() {
        if (escapedAnimal != null && escapedAnimal.isValid() && escapedAnimal.isLeashed()
                && npc.isSpawned() && npc.getEntity().equals(escapedAnimal.getLeashHolder())) {
            escapedAnimal.setLeashHolder(null);
        }
        escapedAnimal = null;
    }

    private boolean tryPatrol(Location zone, long serverTick, LivingNpcConfig config) {
        if (patrolTarget != null && npc.getNavigator().isNavigating()
                && serverTick - navigationStartedTick < config.navigationTimeoutTicks()) return true;
        patrolTarget = safeRanchTarget(zone, serverTick, config);
        if (patrolTarget == null) {
            nextActionTick = serverTick + config.rancher().patrolIntervalTicks();
            return false;
        }
        navigateTo(
                patrolTarget, npc.getUniqueId(), serverTick, config,
                config.navigationDistanceMargin(), "RANCH_PATROL");
        nextActionTick = serverTick + config.rancher().patrolIntervalTicks();
        releasePen();
        return true;
    }

    private boolean enterPen(Location zone, long serverTick, LivingNpcConfig config) {
        Location current = npc.getEntity().getLocation();
        if (inside(current, zone, config.workZoneValidationRadius() - 1,
                config.workZoneValidationVerticalRange())) {
            if (gateRouteCoordinator != null) {
                gateRouteCoordinator.cancel();
                gateRouteCoordinator = null;
            }
            workEntryTarget = null;
            return true;
        }
        if (gateRouteCoordinator != null) {
            GateRouteCoordinator.Result result = gateRouteCoordinator.tick(current, serverTick);
            if (result == GateRouteCoordinator.Result.IN_PROGRESS) return false;
            gateRouteCoordinator = null;
            workEntryTarget = null;
            if (result == GateRouteCoordinator.Result.COMPLETE
                    && inside(current, zone, config.workZoneValidationRadius() - 1,
                            config.workZoneValidationVerticalRange())) {
                nextActionTick = serverTick;
                return true;
            }
            navigationFailure = "Không thể đi qua cổng vào khu chăn nuôi";
            releasePen();
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            return false;
        }
        if (workEntryTarget != null) {
            double margin = config.navigationDistanceMargin();
            if (FarmerRuntime.navigationTargetReached(current, workEntryTarget, margin)) {
                if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
                workEntryTarget = null;
                if (inside(current, zone, config.workZoneValidationRadius() - 1,
                        config.workZoneValidationVerticalRange())) {
                    nextActionTick = serverTick;
                    return true;
                }
            }
            if (npc.getNavigator().isNavigating()
                    && serverTick - navigationStartedTick < config.navigationTimeoutTicks()) return false;
            workEntryTarget = null;
            releasePen();
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            return false;
        }
        if (serverTick < nextActionTick) return false;
        Location target = safeRanchTarget(zone, serverTick, config);
        if (target == null) {
            navigationFailure = "Không tìm được đường đi vào bên trong khu chăn nuôi";
            releasePen();
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            return false;
        }
        VillageDefinition configuredVillage = villages.get(definition.villageId());
        List<NavigationGate> configuredGates = configuredVillage == null
                ? List.of() : configuredVillage.navigationGates();
        List<GateRoute.Candidate> gates = GateRouteDiscovery.discover(
                current, target, configuredGates, definition.activeRole());
        if (!gates.isEmpty()) {
            net.citizensnpcs.api.ai.Navigator gateNavigator = npc.getNavigator();
            LivingNavigation.allowDoors(gateNavigator.getLocalParameters())
                    .speedModifier(config.navigationSpeedModifier())
                    .distanceMargin(config.navigationDistanceMargin())
                    .pathDistanceMargin(config.navigationDistanceMargin())
                    .destinationTeleportMargin(0.0)
                    .stuckAction((stuckNpc, stuckNavigator) -> false);
            gateRouteCoordinator = new GateRouteCoordinator(new GateRouteCoordinator.Navigation() {
                @Override
                public boolean start(Location legTarget, double margin) {
                    return start(legTarget, margin, null, 0L);
                }

                @Override
                public boolean start(
                        Location legTarget, double margin, GateRoute.Leg leg, long generation,
                        GateRoute.Candidate routeCandidate) {
                    net.citizensnpcs.api.ai.NavigatorParameters parameters = gateNavigator.getLocalParameters();
                    Location legStart = npc.getEntity().getLocation();
                    if (!NavigationDiagnostics.shared().targetInRange(
                            legStart, legTarget, parameters.range())) {
                        NavigationDiagnostics.shared().targetOutOfRange(
                                npc, parameters, "RANCH_ENTER_GATE", legTarget,
                                margin, margin);
                        return false;
                    }
                    if (leg == GateRoute.Leg.STAGING || leg == GateRoute.Leg.APPROACH
                            || leg == GateRoute.Leg.EXIT) {
                        if (routeCandidate == null) return false;
                        LivingNavigation.constrainGateRoute(parameters, routeCandidate, leg);
                    } else {
                        LivingNavigation.clearGateRouteConstraint(parameters);
                    }
                    if (!MovementService.startSimpleNavigation(
                            gateNavigator, legTarget, config.navigationSpeedModifier(), 0.0)) return false;
                    net.citizensnpcs.api.ai.NavigatorParameters activeParameters = NavigationDiagnostics.shared()
                            .activeParametersAfterTarget(gateNavigator, legTarget, 0.0);
                    LivingNavigation.allowDoors(activeParameters)
                            .speedModifier(config.navigationSpeedModifier())
                            .distanceMargin(0.0)
                            .pathDistanceMargin(0.0)
                            .destinationTeleportMargin(0.0)
                            .stuckAction((stuckNpc, stuckNavigator) -> false);
                    if (leg == GateRoute.Leg.STAGING || leg == GateRoute.Leg.APPROACH
                            || leg == GateRoute.Leg.EXIT) {
                        LivingNavigation.constrainGateRoute(activeParameters, routeCandidate, leg);
                    } else {
                        LivingNavigation.clearGateRouteConstraint(activeParameters);
                    }
                    NavigationDiagnostics.shared().attach(
                            npc, activeParameters, "RANCH_ENTER_GATE", legTarget,
                            margin, margin);
                    return true;
                }

                @Override
                public boolean navigating() {
                    return gateNavigator.isNavigating();
                }

                @Override
                public void cancel() {
                    if (gateNavigator.isNavigating()) gateNavigator.cancelNavigation();
                }

                @Override
                public boolean requestGate(String gateKey) {
                    return GatePassageService.request(npc, gateKey);
                }

                @Override
                public void releaseGate(String gateKey) {
                    GatePassageService.release(npc, gateKey);
                }
            }, config.navigationTimeoutTicks(), config.navigationDistanceMargin(), 1.0);
            workEntryTarget = target;
            GateRouteCoordinator.Result result = gateRouteCoordinator.start(current, target, gates, serverTick);
            if (result == GateRouteCoordinator.Result.IN_PROGRESS) {
                navigationFailure = null;
                return false;
            }
            gateRouteCoordinator = null;
            workEntryTarget = null;
        }
        net.citizensnpcs.api.ai.Navigator navigator = npc.getNavigator();
        LivingNavigation.allowDoors(navigator.getLocalParameters())
                .speedModifier(config.navigationSpeedModifier())
                .distanceMargin(config.navigationDistanceMargin())
                .pathDistanceMargin(config.navigationDistanceMargin())
                .destinationTeleportMargin(0.0)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
        net.citizensnpcs.api.ai.NavigatorParameters parameters = navigator.getLocalParameters();
        if (!startNavigation(
                navigator, parameters, target, "RANCH_ENTER", serverTick,
                config.navigationDistanceMargin(), config)) {
            navigationFailure = "Mục tiêu vào chuồng vượt quá tầm Citizens";
            releasePen();
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            return false;
        }
        workEntryTarget = target;
        navigationFailure = null;
        return false;
    }

    void observeGateOpened(String gateKey) {
        if (gateRouteCoordinator != null) gateRouteCoordinator.gateOpenIntent(gateKey);
    }

    private Location safeRanchTarget(Location zone, long salt, LivingNpcConfig config) {
        int radius = config.workZoneValidationRadius();
        Location current = npc.getEntity().getLocation();
        java.util.ArrayList<Location> candidates = new java.util.ArrayList<>();
        for (int x = -radius + 1; x < radius; x++) for (int z = -radius + 1; z < radius; z++) {
            org.bukkit.block.Block feet = zone.getWorld().getBlockAt(
                    zone.getBlockX() + x, zone.getBlockY(), zone.getBlockZ() + z);
            if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()
                    || !feet.getRelative(0, -1, 0).getType().isSolid()) continue;
            candidates.add(feet.getLocation().add(0.5, 0, 0.5));
        }
        return candidates.stream()
                .min(Comparator.comparingDouble(current::distanceSquared))
                .orElse(null);
    }

    private void navigateTo(
            Location target, UUID targetUuid, long serverTick, LivingNpcConfig config,
            double margin, String operation) {
        net.citizensnpcs.api.ai.Navigator navigator = npc.getNavigator();
        LivingNavigation.allowDoors(navigator.getLocalParameters())
                .speedModifier(config.navigationSpeedModifier())
                .distanceMargin(margin)
                .pathDistanceMargin(margin)
                .destinationTeleportMargin(0.0)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
        net.citizensnpcs.api.ai.NavigatorParameters parameters = navigator.getLocalParameters();
        if (!startNavigation(navigator, parameters, target, operation, serverTick, margin, config)) {
            navigationFailure = "Mục tiêu Rancher vượt quá tầm Citizens";
            navigationTarget = null;
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            return;
        }
        navigationTarget = targetUuid;
    }

    private boolean startNavigation(
            net.citizensnpcs.api.ai.Navigator navigator,
            net.citizensnpcs.api.ai.NavigatorParameters parameters,
            Location target, String operation, long serverTick, double margin, LivingNpcConfig config) {
        Location current = npc.getEntity().getLocation();
        if (!NavigationDiagnostics.shared().targetInRange(current, target, parameters.range())) {
            NavigationDiagnostics.shared().targetOutOfRange(npc, parameters, operation, target, margin, margin);
            return false;
        }
        if (!MovementService.startSimpleNavigation(
                navigator, target, config.navigationSpeedModifier(), margin)) return false;
        net.citizensnpcs.api.ai.NavigatorParameters activeParameters = NavigationDiagnostics.shared()
                .activeParametersAfterTarget(navigator, target, margin);
        NavigationDiagnostics.shared().attach(npc, activeParameters, operation, target, margin, margin);
        navigationStartedTick = serverTick;
        return true;
    }

    static double distanceOutsideSquared(Location location, Location center) {
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        return dx * dx + dz * dz;
    }

    private boolean tryCull(
            List<Animals> animals, VillageDefinition village, long serverTick, LivingNpcConfig config) {
        for (Class<? extends Animals> species : supportedSpecies()) {
            List<Animals> group = animals.stream().filter(species::isInstance).toList();
            int excess = group.size() - village.ranchAnimalLimit();
            if (excess <= 0) continue;
            List<Animals> adults = group.stream().filter(Animals::isAdult)
                    .filter(animal -> !animal.isLoveMode())
                    .sorted(Comparator.comparingDouble((Animals animal) ->
                                    npc.getEntity().getLocation().distanceSquared(animal.getLocation()))
                            .thenComparing(animal -> animal.getUniqueId().toString()))
                    .toList();
            int count = Math.min(config.rancher().maxCullPerCycle(), Math.min(excess, adults.size() - 2));
            if (count <= 0 || !economy.canStoreVillageItems(village.id(), count * 8)) continue;
            for (Animals animal : adults.stream().limit(count).toList()) {
                if (!approach(animal, serverTick, config)) return true;
                npc.getOrAddTrait(net.citizensnpcs.api.trait.trait.Equipment.class)
                        .set(net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot.HAND,
                                new ItemStack(Material.IRON_SWORD));
                faceAnimal(animal);
                if (npc.getEntity() instanceof LivingEntity living) living.swingMainHand();
                culler.accept(animal);
                experienceAwarder.accept(10L);
                economy.recordActivity(
                        npc.getUniqueId(), village.id(), ResidentRole.RANCHER,
                        "Xử lý vật nuôi dư", animal.getType().getKey().getKey(), 1);
            }
            setHand(null);
            nextActionTick = serverTick + config.rancher().actionCooldownTicks();
            releasePen();
            return true;
        }
        return false;
    }

    private boolean tryBreed(
            List<Animals> animals, VillageDefinition village, long serverTick, LivingNpcConfig config) {
        for (Class<? extends Animals> species : supportedSpecies()) {
            List<Animals> group = animals.stream().filter(species::isInstance).toList();
            if (group.size() >= village.ranchAnimalLimit()) continue;
            List<Animals> ready = group.stream().filter(Animals::isAdult)
                    .filter(Animals::canBreed).filter(animal -> !animal.isLoveMode())
                    .sorted(Comparator.comparingDouble(animal ->
                            npc.getEntity().getLocation().distanceSquared(animal.getLocation())))
                    .limit(2).toList();
            if (ready.size() < 2) continue;
            String food = foodKey(ready.getFirst());
            if (food == null || economy.villageAccount(village.id()).quantity(food) < 2) continue;
            feedingAnimals = List.copyOf(ready);
            feedingIndex = 0;
            feedingFood = food;
            failedDeliveryLocations.clear();
            foodChest = nearestReachableDeliveryChest(village.id(), config);
            if (foodChest == null) {
                feedingAnimals = List.of();
                feedingFood = null;
                nextActionTick = serverTick + config.navigationRetryBackoffTicks();
                releasePen();
                return false;
            }
            fetchingFood = true;
            setHand(null);
            nextActionTick = serverTick;
            navigationFailure = null;
            return true;
        }
        nextActionTick = serverTick + config.rancher().scanIntervalTicks();
        return false;
    }

    private boolean approach(Animals animal, long serverTick, LivingNpcConfig config) {
        return approach(animal.getLocation(), animal.getUniqueId(), serverTick, config);
    }

    private boolean approach(Location target, UUID targetUuid, long serverTick, LivingNpcConfig config) {
        if (npc.getEntity().getLocation().distanceSquared(target)
                <= config.rancher().interactionRange() * config.rancher().interactionRange()) {
            if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
            navigationTarget = null;
            navigationFailure = null;
            return true;
        }
        if (targetUuid.equals(navigationTarget) && npc.getNavigator().isNavigating()
                && serverTick - navigationStartedTick < config.navigationTimeoutTicks()) return false;
        if (targetUuid.equals(navigationTarget)) {
            NavigationRecovery.Result recovery = NavigationRecovery.recover(
                    npc, target, 2, serverTick, "RANCH_APPROACH", config.navigationRetryBackoffTicks());
            if (recovery == NavigationRecovery.Result.RECOVERED) {
                navigationFailure = "Đã khôi phục về ô đứng an toàn; chưa xác nhận tới vật nuôi";
            }
            navigationTarget = null;
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            return false;
        }
        net.citizensnpcs.api.ai.Navigator navigator = npc.getNavigator();
        net.citizensnpcs.api.ai.NavigatorParameters parameters = LivingNavigation.allowDoors(navigator.getLocalParameters())
                .speedModifier(config.navigationSpeedModifier())
                .distanceMargin(config.rancher().interactionRange())
                .destinationTeleportMargin(0.0)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
        Location standing = standingNear(target, navigator, parameters);
        if (standing == null) {
            navigationTarget = null;
            navigationFailure = "Không tìm được ô đứng an toàn hoặc đường đi cạnh vật nuôi";
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            return false;
        }
        if (!startNavigation(
                navigator, parameters, standing, "RANCH_APPROACH_ANIMAL", serverTick,
                config.rancher().interactionRange(), config)) {
            navigationTarget = null;
            navigationFailure = "Vật nuôi vượt quá tầm Citizens";
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            return false;
        }
        navigationTarget = targetUuid;
        navigationFailure = null;
        return false;
    }

    private void continueFeeding(long serverTick, LivingNpcConfig config) {
        if (serverTick < nextActionTick) return;
        if (feedingIndex >= feedingAnimals.size()) {
            feedingAnimals = List.of();
            feedingIndex = 0;
            feedingFood = null;
            setHand(null);
            nextActionTick = serverTick + config.rancher().actionCooldownTicks();
            releasePen();
            return;
        }
        Animals animal = feedingAnimals.get(feedingIndex);
        if (!animal.isValid() || !animal.isAdult() || !animal.canBreed()) {
            feedingAnimals = List.of();
            feedingIndex = 0;
            feedingFood = null;
            setHand(null);
            nextActionTick = serverTick + config.rancher().scanIntervalTicks();
            releasePen();
            return;
        }
        if (!approach(animal, serverTick, config)) return;
        Material held = switch (feedingFood) {
            case "wheat" -> Material.WHEAT;
            case "wheat_seeds" -> Material.WHEAT_SEEDS;
            case "carrot" -> Material.CARROT;
            default -> Material.AIR;
        };
        setHand(new ItemStack(held));
        faceAnimal(animal);
        if (!economy.consumeVillageItem(definition.villageId(), feedingFood, 1)) {
            releaseWorkState();
            nextActionTick = serverTick + config.rancher().scanIntervalTicks();
            return;
        }
        if (npc.getEntity() instanceof LivingEntity living) living.swingMainHand();
        animal.setLoveModeTicks(config.rancher().loveModeTicks());
        animal.setBreedCause(npc.getUniqueId());
        feedingIndex++;
        if (feedingIndex >= feedingAnimals.size()) {
            experienceAwarder.accept(10L);
            economy.recordActivity(
                    npc.getUniqueId(), definition.villageId(), ResidentRole.RANCHER,
                    "Cho vật nuôi ăn", feedingFood, feedingAnimals.size());
        }
        nextActionTick = serverTick + 20L;
    }

    private void continueFetchingFood(long serverTick, LivingNpcConfig config) {
        if (foodChest == null || feedingFood == null) {
            releaseWorkState();
            return;
        }
        Location current = npc.getEntity().getLocation();
        if (!current.getWorld().equals(foodChest.getWorld())) {
            releaseWorkState();
            return;
        }
        if (current.distanceSquared(foodChest) <= config.rancher().interactionRange()
                * config.rancher().interactionRange()) {
            if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
            if (economy.villageAccount(definition.villageId()).quantity(feedingFood) < 2) {
                releaseWorkState();
                nextActionTick = serverTick + config.rancher().scanIntervalTicks();
                return;
            }
            faceLocation(foodChest.clone().add(0.5, 0.5, 0.5));
            foodChest.getWorld().playSound(foodChest, org.bukkit.Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
            setHand(new ItemStack(foodMaterial(feedingFood)));
            if (npc.getEntity() instanceof LivingEntity living) living.swingMainHand();
            foodChest.getWorld().playSound(foodChest, org.bukkit.Sound.BLOCK_CHEST_CLOSE, 0.7f, 1.0f);
            fetchingFood = false;
            failedDeliveryLocations.clear();
            navigationTarget = null;
            nextActionTick = serverTick + 20L;
            return;
        }
        if (navigationTarget != null && npc.getNavigator().isNavigating()
                && serverTick - navigationStartedTick < config.navigationTimeoutTicks()) return;
        if (navigationTarget != null) {
            failedDeliveryLocations.add(deliveryKey(foodChest));
            foodChest = nearestReachableDeliveryChest(definition.villageId(), config);
            navigationTarget = null;
            if (foodChest == null) {
                navigationFailure = "Không có rương kho nào còn đường đi để lấy thức ăn";
                releaseWorkState();
                nextActionTick = serverTick + config.navigationRetryBackoffTicks();
                failedDeliveryLocations.clear();
            }
            return;
        }
        net.citizensnpcs.api.ai.Navigator navigator = npc.getNavigator();
        net.citizensnpcs.api.ai.NavigatorParameters parameters = LivingNavigation.allowDoors(
                        navigator.getLocalParameters())
                .speedModifier(config.navigationSpeedModifier())
                .distanceMargin(config.rancher().interactionRange())
                .destinationTeleportMargin(0.0)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
        Location standing = standingNear(foodChest, navigator, parameters);
        if (standing == null) {
            releaseWorkState();
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            return;
        }
        if (!startNavigation(
                navigator, parameters, standing, "RANCH_FETCH_FOOD", serverTick,
                config.rancher().interactionRange(), config)) {
            navigationFailure = "Rương thức ăn vượt quá tầm Citizens";
            failedDeliveryLocations.add(deliveryKey(foodChest));
            foodChest = null;
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            return;
        }
        navigationTarget = npc.getUniqueId();
    }

    private Location nearestReachableDeliveryChest(String villageId, LivingNpcConfig config) {
        Location current = npc.getEntity().getLocation();
        net.citizensnpcs.api.ai.Navigator navigator = npc.getNavigator();
        net.citizensnpcs.api.ai.NavigatorParameters parameters = LivingNavigation.allowDoors(
                        navigator.getLocalParameters())
                .speedModifier(config.navigationSpeedModifier())
                .distanceMargin(config.rancher().interactionRange())
                .destinationTeleportMargin(0.0)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
        double rangeSquared = config.rancher().interactionRange() * config.rancher().interactionRange();
        return nearestReachable(
                villages.deliveryChests(villageId), current,
                chest -> !failedDeliveryLocations.contains(deliveryKey(chest))
                        && (current.distanceSquared(chest) <= rangeSquared
                                || standingNear(chest, navigator, parameters) != null));
    }

    static Location nearestReachable(
            List<Location> candidates, Location current, Predicate<Location> reachable) {
        return candidates.stream()
                .filter(candidate -> current.getWorld().equals(candidate.getWorld()))
                .sorted(Comparator.comparingDouble(current::distanceSquared))
                .filter(reachable)
                .findFirst().orElse(null);
    }

    private StoredLocation deliveryKey(Location location) {
        return new StoredLocation(
                location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), 0, 0);
    }

    private Material foodMaterial(String food) {
        return switch (food) {
            case "wheat" -> Material.WHEAT;
            case "wheat_seeds" -> Material.WHEAT_SEEDS;
            case "carrot" -> Material.CARROT;
            default -> Material.AIR;
        };
    }

    private boolean tryCollectProduct(
            Location zone, VillageDefinition village, long serverTick, LivingNpcConfig config) {
        int radius = config.workZoneValidationRadius();
        Item product = zone.getWorld().getNearbyEntitiesByType(
                Item.class, zone, radius, config.workZoneValidationVerticalRange(), radius,
                item -> item.getThrower() == null && ranchProduct(item.getItemStack().getType())
                        && inside(item.getLocation(), zone, radius, config.workZoneValidationVerticalRange()))
                .stream().min(Comparator.comparingDouble(item ->
                        npc.getEntity().getLocation().distanceSquared(item.getLocation()))).orElse(null);
        if (product == null) return false;
        if (!approach(product.getLocation(), product.getUniqueId(), serverTick, config)) return true;
        int amount = product.getItemStack().getAmount();
        String itemKey = product.getItemStack().getType().getKey().getKey();
        faceLocation(product.getLocation().clone().add(0, 0.25, 0));
        if (!economy.addCarriedLoot(npc.getUniqueId(), Map.of(itemKey, amount))) {
            depositCarriedLoot(serverTick, config, village.id());
            return true;
        }
        if (npc.getEntity() instanceof LivingEntity living) living.swingMainHand();
        product.remove();
        economy.recordActivity(
                npc.getUniqueId(), village.id(), ResidentRole.RANCHER,
                "Thu gom sản phẩm chuồng", itemKey, amount);
        nextActionTick = serverTick + config.rancher().scanIntervalTicks();
        releasePen();
        return true;
    }

    static boolean ranchProduct(Material material) {
        return material == Material.EGG || material.name().endsWith("_WOOL");
    }

    private StoredLocation selectPen(VillageDefinition village, LivingNpcConfig config) {
        List<RanchPen> pens = village.ranchPens();
        for (int offset = 0; offset < pens.size(); offset++) {
            int index = (nextPenIndex + offset) % pens.size();
            StoredLocation candidate = pens.get(index).center();
            Location location = candidate.resolve();
            if (location == null || !npc.getEntity().getWorld().equals(location.getWorld())
                    || !RuntimeChunkAvailability.loadedArea(location, config.workZoneValidationRadius())
                    || !WorkZoneValidator.validate(
                            location, VillageWorkZoneType.RANCH,
                            config.workZoneValidationRadius(), config.workZoneValidationVerticalRange()).valid()
                    || workCoordinator.occupiedByOther(village.id(), npc.getUniqueId(), candidate,
                            config.workZoneValidationRadius())
                    || !canReachPen(location, offset, config)) continue;
            nextPenIndex = (index + 1) % pens.size();
            return candidate;
        }
        return null;
    }


    private boolean canReachPen(Location center, int offset, LivingNpcConfig config) {
        Location current = npc.getEntity().getLocation();
        return inside(current, center, config.workZoneValidationRadius(), config.workZoneValidationVerticalRange())
                || safeRanchTarget(center, nextPenIndex + offset, config) != null;
    }

    private boolean depositCarriedLoot(long serverTick, LivingNpcConfig config, String villageId) {
        if (carriedDeliveryChest == null) carriedDeliveryChest = nearestReachableDeliveryChest(villageId, config);
        Location chest = carriedDeliveryChest;
        if (chest == null) {
            navigationFailure = "Không có rương kho nào còn đường đi để giao sản phẩm";
            releasePen();
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            failedDeliveryLocations.clear();
            return true;
        }
        Location current = npc.getEntity().getLocation();
        double range = config.rancher().interactionRange();
        if (current.distanceSquared(chest) <= range * range) {
            if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
            faceLocation(chest.clone().add(0.5, 0.5, 0.5));
            chest.getWorld().playSound(chest, org.bukkit.Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
            if (economy.depositCarriedLoot(npc.getUniqueId(), villageId)
                    && npc.getEntity() instanceof LivingEntity living) living.swingMainHand();
            chest.getWorld().playSound(chest, org.bukkit.Sound.BLOCK_CHEST_CLOSE, 0.7f, 1.0f);
            navigationTarget = null;
            carriedDeliveryChest = null;
            failedDeliveryLocations.clear();
            navigationFailure = null;
            setHand(null);
            nextActionTick = serverTick + config.rancher().actionCooldownTicks();
            releasePen();
            return true;
        }
        if (navigationTarget != null && npc.getNavigator().isNavigating()
                && serverTick - navigationStartedTick < config.navigationTimeoutTicks()) return true;
        if (navigationTarget != null) {
            failedDeliveryLocations.add(deliveryKey(chest));
            carriedDeliveryChest = nearestReachableDeliveryChest(villageId, config);
            navigationTarget = null;
            nextActionTick = serverTick + 1L;
            return true;
        }
        net.citizensnpcs.api.ai.Navigator navigator = npc.getNavigator();
        net.citizensnpcs.api.ai.NavigatorParameters parameters = LivingNavigation.allowDoors(
                        navigator.getLocalParameters())
                .speedModifier(config.navigationSpeedModifier())
                .distanceMargin(range)
                .destinationTeleportMargin(0.0)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
        Location standing = standingNear(chest, navigator, parameters);
        if (standing == null) {
            failedDeliveryLocations.add(deliveryKey(chest));
            carriedDeliveryChest = null;
            nextActionTick = serverTick + 1L;
            return true;
        }
        if (!startNavigation(
                navigator, parameters, standing, "RANCH_DEPOSIT", serverTick, range, config)) {
            navigationFailure = "Rương giao sản phẩm vượt quá tầm Citizens";
            failedDeliveryLocations.add(deliveryKey(chest));
            carriedDeliveryChest = null;
            nextActionTick = serverTick + config.navigationRetryBackoffTicks();
            return true;
        }
        navigationTarget = npc.getUniqueId();
        setHand(null);
        return true;
    }

    private Location standingNear(
            Location animal, net.citizensnpcs.api.ai.Navigator navigator,
            net.citizensnpcs.api.ai.NavigatorParameters parameters) {
        Location current = npc.getEntity().getLocation();
        return java.util.Arrays.stream(new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                .map(offset -> animal.clone().add(offset[0], 0, offset[1]))
                .filter(candidate -> {
                    org.bukkit.block.Block feet = candidate.getBlock();
                    return feet.isPassable() && feet.getRelative(0, 1, 0).isPassable()
                            && feet.getRelative(0, -1, 0).getType().isSolid();
                })
                .map(candidate -> candidate.getBlock().getLocation().add(0.5, 0, 0.5))
                .min(Comparator.comparingDouble(current::distanceSquared))
                .orElse(null);
    }

    private void releasePen() {
        workCoordinator.release(npc.getUniqueId());
        activePen = null;
    }

    private void setHand(ItemStack item) {
        npc.getOrAddTrait(net.citizensnpcs.api.trait.trait.Equipment.class)
                .set(net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot.HAND, item);
        if (npc.isSpawned() && npc.getEntity() instanceof LivingEntity living) {
            living.getEquipment().setItemInMainHand(item);
        }
    }

    private boolean supported(Animals animal) {
        return animal instanceof Cow || animal instanceof Sheep || animal instanceof Chicken
                || animal instanceof Pig || animal instanceof Rabbit;
    }

    private String foodKey(Animals animal) {
        if (animal instanceof Cow || animal instanceof Sheep) return "wheat";
        if (animal instanceof Chicken) return "wheat_seeds";
        if (animal instanceof Pig) return "carrot";
        if (animal instanceof Rabbit) return "carrot";
        return null;
    }

    private List<Class<? extends Animals>> supportedSpecies() {
        return List.of(Cow.class, Sheep.class, Chicken.class, Pig.class, Rabbit.class);
    }

    private String speciesName(Animals animal) {
        if (animal instanceof Cow) return "bò";
        if (animal instanceof Sheep) return "cừu";
        if (animal instanceof Chicken) return "gà";
        if (animal instanceof Pig) return "lợn";
        if (animal instanceof Rabbit) return "thỏ";
        return "vật nuôi";
    }

    private boolean inside(Location location, Location center, int radius, int vertical) {
        return location.getWorld().equals(center.getWorld())
                && Math.abs(location.getBlockX() - center.getBlockX()) <= radius
                && Math.abs(location.getBlockY() - center.getBlockY()) <= vertical
                && Math.abs(location.getBlockZ() - center.getBlockZ()) <= radius;
    }

    private void faceAnimal(Animals animal) {
        Location origin = npc.getEntity() instanceof LivingEntity living
                ? living.getEyeLocation() : npc.getEntity().getLocation().add(0, 1.6, 0);
        Location target = animal.getEyeLocation();
        double dx = target.getX() - origin.getX();
        double dz = target.getZ() - origin.getZ();
        npc.getEntity().setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)), 0.0f);
    }

    private void faceLocation(Location target) {
        Location origin = npc.getEntity() instanceof LivingEntity living
                ? living.getEyeLocation() : npc.getEntity().getLocation().add(0, 1.6, 0);
        double dx = target.getX() - origin.getX();
        double dz = target.getZ() - origin.getZ();
        npc.getEntity().setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)), 0.0f);
    }
}
