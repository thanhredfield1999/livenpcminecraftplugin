package vn.heomc.livingnpc;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private List<Animals> feedingAnimals = List.of();
    private int feedingIndex;
    private String feedingFood;
    private Location foodChest;
    private boolean fetchingFood;
    private final java.util.Set<UUID> knownHerd = new java.util.HashSet<>();
    private Animals escapedAnimal;
    private Location returnTarget;
    private Location patrolTarget;

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
        if (villageChanged) releaseWorkState();
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
        StoredLocation storedZone = village == null ? null : village.workZone(VillageWorkZoneType.RANCH);
        Location zone = storedZone == null ? null : storedZone.resolve();
        if (zone == null || !npc.getEntity().getWorld().equals(zone.getWorld())
                || zone.getWorld().getNearbyPlayers(zone, config.activationRange()).isEmpty()) {
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
        if (!WorkZoneValidator.validate(
                zone, VillageWorkZoneType.RANCH,
                config.workZoneValidationRadius(), config.workZoneValidationVerticalRange()).valid()) {
            suspend();
            return;
        }
        if (serverTick < nextActionTick) return;
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
        animals.forEach(animal -> knownHerd.add(animal.getUniqueId()));
        if (tryCollectEgg(zone, village, serverTick, config)) return;
        if (tryReturnEscaped(zone, village.id(), serverTick, config)) return;
        if (tryCull(animals, village, serverTick, config)) return;
        if (tryBreed(animals, village, serverTick, config)) return;
        if (tryPatrol(zone, serverTick, config)) return;
        workCoordinator.release(npc.getUniqueId());
    }

    void suspend() {
        if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
        releaseWorkState();
    }

    void releaseWorkState() {
        workCoordinator.release(npc.getUniqueId());
        navigationTarget = null;
        feedingAnimals = List.of();
        feedingIndex = 0;
        feedingFood = null;
        foodChest = null;
        fetchingFood = false;
        releaseEscapedAnimal();
        patrolTarget = null;
        setHand(null);
    }

    FarmerPhase phase() {
        return navigationTarget != null && npc.getNavigator().isNavigating()
                ? FarmerPhase.GOING_TO_WORK_STATION : FarmerPhase.RESTING;
    }

    boolean taskActive() {
        return navigationTarget != null || !feedingAnimals.isEmpty() || escapedAnimal != null;
    }

    String status(LivingNpcConfig config) {
        if (!npc.isSpawned()) return "NPC chưa spawn";
        if (!definition.enabled(BehaviorFlag.MASTER)) return "NPC hoạt động đang TẮT";
        VillageDefinition village = villages.get(definition.villageId());
        StoredLocation storedZone = village == null ? null : village.workZone(VillageWorkZoneType.RANCH);
        Location zone = storedZone == null ? null : storedZone.resolve();
        if (zone == null) return "Chưa đặt Khu chăn nuôi";
        if (!npc.getEntity().getWorld().equals(zone.getWorld())) return "NPC và Khu chăn nuôi khác world";
        if (zone.getWorld().getNearbyPlayers(zone, config.activationRange()).isEmpty()) {
            return "Không có người chơi trong " + (int) config.activationRange() + " block";
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
        navigateTo(returnTarget, animal.getUniqueId(), serverTick, config, margin);
    }

    private void finishEscapedReturn(long serverTick, LivingNpcConfig config, boolean success) {
        releaseEscapedAnimal();
        returnTarget = null;
        navigationTarget = null;
        setHand(null);
        workCoordinator.release(npc.getUniqueId());
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
        navigateTo(patrolTarget, npc.getUniqueId(), serverTick, config, config.navigationDistanceMargin());
        nextActionTick = serverTick + config.rancher().patrolIntervalTicks();
        workCoordinator.release(npc.getUniqueId());
        return true;
    }

    private Location safeRanchTarget(Location zone, long salt, LivingNpcConfig config) {
        int radius = config.workZoneValidationRadius();
        java.util.Random random = new java.util.Random(salt ^ npc.getUniqueId().getLeastSignificantBits());
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = random.nextInt(-radius + 1, radius);
            int z = random.nextInt(-radius + 1, radius);
            org.bukkit.block.Block feet = zone.getWorld().getBlockAt(
                    zone.getBlockX() + x, zone.getBlockY(), zone.getBlockZ() + z);
            if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()
                    || !feet.getRelative(0, -1, 0).getType().isSolid()) continue;
            Location candidate = feet.getLocation().add(0.5, 0, 0.5);
            net.citizensnpcs.api.ai.NavigatorParameters parameters = LivingNavigation.allowDoors(
                    npc.getNavigator().getLocalParameters());
            if (npc.getNavigator().canNavigateTo(candidate, parameters)) return candidate;
        }
        return null;
    }

    private void navigateTo(
            Location target, UUID targetUuid, long serverTick, LivingNpcConfig config, double margin) {
        net.citizensnpcs.api.ai.Navigator navigator = npc.getNavigator();
        LivingNavigation.allowDoors(navigator.getLocalParameters())
                .speedModifier(config.navigationSpeedModifier())
                .distanceMargin(margin)
                .destinationTeleportMargin(0.0)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
        navigator.setTarget(target);
        navigationTarget = targetUuid;
        navigationStartedTick = serverTick;
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
            workCoordinator.release(npc.getUniqueId());
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
            foodChest = nearestDeliveryChest(village.id());
            if (foodChest == null) {
                feedingAnimals = List.of();
                feedingFood = null;
                nextActionTick = serverTick + config.navigationRetryBackoffTicks();
                workCoordinator.release(npc.getUniqueId());
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
        navigator.setTarget(standing);
        navigationTarget = targetUuid;
        navigationStartedTick = serverTick;
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
            workCoordinator.release(npc.getUniqueId());
            return;
        }
        Animals animal = feedingAnimals.get(feedingIndex);
        if (!animal.isValid() || !animal.isAdult() || !animal.canBreed()) {
            feedingAnimals = List.of();
            feedingIndex = 0;
            feedingFood = null;
            setHand(null);
            nextActionTick = serverTick + config.rancher().scanIntervalTicks();
            workCoordinator.release(npc.getUniqueId());
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
            navigationTarget = null;
            nextActionTick = serverTick + 20L;
            return;
        }
        if (navigationTarget != null && npc.getNavigator().isNavigating()
                && serverTick - navigationStartedTick < config.navigationTimeoutTicks()) return;
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
        navigator.setTarget(standing);
        navigationTarget = npc.getUniqueId();
        navigationStartedTick = serverTick;
    }

    private Location nearestDeliveryChest(String villageId) {
        Location current = npc.getEntity().getLocation();
        return villages.deliveryChests(villageId).stream()
                .filter(chest -> current.getWorld().equals(chest.getWorld()))
                .min(Comparator.comparingDouble(current::distanceSquared))
                .orElse(null);
    }

    private Material foodMaterial(String food) {
        return switch (food) {
            case "wheat" -> Material.WHEAT;
            case "wheat_seeds" -> Material.WHEAT_SEEDS;
            case "carrot" -> Material.CARROT;
            default -> Material.AIR;
        };
    }

    private boolean tryCollectEgg(
            Location zone, VillageDefinition village, long serverTick, LivingNpcConfig config) {
        int radius = config.workZoneValidationRadius();
        Item egg = zone.getWorld().getNearbyEntitiesByType(
                Item.class, zone, radius, config.workZoneValidationVerticalRange(), radius,
                item -> item.getItemStack().getType() == Material.EGG
                        && inside(item.getLocation(), zone, radius, config.workZoneValidationVerticalRange()))
                .stream().min(Comparator.comparingDouble(item ->
                        npc.getEntity().getLocation().distanceSquared(item.getLocation()))).orElse(null);
        if (egg == null) return false;
        if (!approach(egg.getLocation(), egg.getUniqueId(), serverTick, config)) return true;
        int amount = egg.getItemStack().getAmount();
        if (!economy.addCarriedLoot(npc.getUniqueId(), Map.of("egg", amount))) {
            depositCarriedLoot(serverTick, config, village.id());
            return true;
        }
        if (npc.getEntity() instanceof LivingEntity living) living.swingMainHand();
        egg.remove();
        economy.recordActivity(
                npc.getUniqueId(), village.id(), ResidentRole.RANCHER,
                "Thu hoạch trứng", "egg", amount);
        nextActionTick = serverTick + config.rancher().scanIntervalTicks();
        workCoordinator.release(npc.getUniqueId());
        return true;
    }

    private boolean depositCarriedLoot(long serverTick, LivingNpcConfig config, String villageId) {
        Location chest = nearestDeliveryChest(villageId);
        if (chest == null) return false;
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
            setHand(null);
            nextActionTick = serverTick + config.rancher().actionCooldownTicks();
            return true;
        }
        if (navigationTarget != null && npc.getNavigator().isNavigating()
                && serverTick - navigationStartedTick < config.navigationTimeoutTicks()) return true;
        net.citizensnpcs.api.ai.Navigator navigator = npc.getNavigator();
        net.citizensnpcs.api.ai.NavigatorParameters parameters = LivingNavigation.allowDoors(
                        navigator.getLocalParameters())
                .speedModifier(config.navigationSpeedModifier())
                .distanceMargin(range)
                .destinationTeleportMargin(0.0)
                .stuckAction((stuckNpc, stuckNavigator) -> false);
        Location standing = standingNear(chest, navigator, parameters);
        if (standing == null) return false;
        navigator.setTarget(standing);
        navigationTarget = npc.getUniqueId();
        navigationStartedTick = serverTick;
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
                .filter(candidate -> navigator.canNavigateTo(candidate, parameters))
                .min(Comparator.comparingDouble(current::distanceSquared))
                .orElse(null);
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
