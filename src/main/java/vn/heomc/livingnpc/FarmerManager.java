package vn.heomc.livingnpc;

import java.util.LinkedHashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.EntityType;

final class FarmerManager {
    private final FarmerStore store;
    private final Map<UUID, FarmerDefinition> definitions;
    private final Map<UUID, FarmerRuntime> runtimes = new LinkedHashMap<>();
    private final NpcEconomy economy;
    private final WorldMutationPolicy mutationPolicy;
    private final VillageStore villageStore;
    private final VillagePathCache pathCache;
    private final SeatManager seatManager;
    private LivingNpcConfig config;
    private boolean progressDirty;
    private long nextRoleSaveAttemptTick;
    private long nextSocialTick;
    private Set<UUID> externallyBusy = Set.of();
    private Set<UUID> rolesChangedThisTick = Set.of();

    FarmerManager(
            FarmerStore store,
            NpcEconomy economy,
            WorldMutationPolicy mutationPolicy,
            VillageStore villageStore,
            LivingNpcConfig config) {
        this.store = store;
        this.economy = economy;
        this.mutationPolicy = mutationPolicy;
        this.villageStore = villageStore;
        this.pathCache = new VillagePathCache(villageStore);
        this.seatManager = new SeatManager(villageStore);
        this.config = config;
        this.definitions = store.load();
        bindLoadedNpcs();
    }

    NPC create(ResidentProfile profile, Location home) {
        return create(profile, home, null);
    }

    NPC create(ResidentProfile profile, Location home, String villageId) {
        if (profile.roles().stream().noneMatch(ResidentRole::implemented)) {
            return null;
        }
        VillageDefinition village = villageId == null ? null : villageStore.get(villageId);
        if (villageId != null && (village == null
                || !home.getWorld().getName().equals(village.center().world()))) {
            return null;
        }
        Location spawn = bedStandingLocation(home);
        if (spawn == null) return null;
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, profile.name());
        configureWorkerNpc(npc);
        if (!profile.skin().isBlank()) {
            npc.getOrAddTrait(SkinTrait.class).setSkinName(profile.skin(), true);
        }
        FarmerDefinition definition = new FarmerDefinition(
                npc.getUniqueId(), villageId, StoredLocation.from(home), null, 4, profile, BehaviorFlag.safeDefaults());
        definitions.put(npc.getUniqueId(), definition);
        runtimes.put(npc.getUniqueId(), createRuntime(npc, definition));
        if (!npc.spawn(spawn)) {
            definitions.remove(npc.getUniqueId());
            runtimes.remove(npc.getUniqueId());
            npc.destroy();
            return null;
        }
        if (!save()) {
            definitions.remove(npc.getUniqueId());
            runtimes.remove(npc.getUniqueId());
            npc.destroy();
            return null;
        }
        return npc;
    }

    boolean adopt(int npcId) {
        return adopt(npcId, null);
    }

    boolean adopt(int npcId, String villageId) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        VillageDefinition village = villageStore.get(villageId);
        if (npc == null || !npc.isSpawned() || definitions.containsKey(npc.getUniqueId()) || village == null
                || !npc.getEntity().getWorld().getName().equals(village.center().world())) {
            return false;
        }
        FarmerDefinition definition = new FarmerDefinition(
                npc.getUniqueId(), village.id(),
                StoredLocation.from(npc.getEntity().getLocation()),
                null,
                4,
                ResidentProfile.custom(npc.getName()),
                BehaviorFlag.safeDefaults());
        definitions.put(npc.getUniqueId(), definition);
        configureWorkerNpc(npc);
        runtimes.put(npc.getUniqueId(), createRuntime(npc, definition));
        if (save()) {
            return true;
        }
        definitions.remove(npc.getUniqueId());
        runtimes.remove(npc.getUniqueId());
        return false;
    }

    boolean setHome(int npcId, Location home) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        return npc != null && setHome(npc.getUniqueId(), home);
    }

    boolean setHome(UUID npcUuid, Location home) {
        FarmerDefinition current = definitions.get(npcUuid);
        if (current == null || !matchesVillageWorld(current, home) || bedStandingLocation(home) == null) {
            return false;
        }
        return update(current.withHome(StoredLocation.from(home)));
    }

    private Location bedStandingLocation(Location bedLocation) {
        if (bedLocation == null || !(bedLocation.getBlock().getBlockData() instanceof Bed)) return null;
        Location current = bedLocation.clone().add(0.5, 0, 0.5);
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Block feet = bedLocation.getBlock().getRelative(offset[0], 0, offset[1]);
            if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()
                    || !feet.getRelative(0, -1, 0).getType().isSolid()) continue;
            Location candidate = feet.getLocation().add(0.5, 0, 0.5);
            double distance = current.distanceSquared(candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    boolean setPlot(int npcId, Location plot, int radius) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        return npc != null && setPlot(npc.getUniqueId(), plot, radius);
    }

    boolean setPlot(UUID npcUuid, Location plot, int radius) {
        FarmerDefinition current = definitions.get(npcUuid);
        if (current == null || !matchesVillageWorld(current, plot)) {
            return false;
        }
        int boundedRadius = Math.clamp(radius, 1, config.maxPlotRadius());
        return update(current.withPlot(StoredLocation.from(plot), boundedRadius));
    }

    boolean assignVillage(int npcId, String villageId) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        return npc != null && assignVillage(npc.getUniqueId(), villageId);
    }

    boolean assignVillage(UUID npcUuid, String villageId) {
        FarmerDefinition current = definitions.get(npcUuid);
        VillageDefinition village = villageStore.get(villageId);
        NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(npcUuid);
        if (current == null || village == null
                || !current.home().world().equals(village.center().world())
                || current.plot() != null && !current.plot().world().equals(village.center().world())
                || npc != null && npc.isSpawned()
                        && !npc.getEntity().getWorld().getName().equals(village.center().world())) {
            return false;
        }
        return update(current.withVillage(village.id()));
    }

    FarmerDefinition get(int npcId) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        return npc == null ? null : definitions.get(npc.getUniqueId());
    }

    FarmerPhase phase(int npcId) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        return npc == null ? null : phase(npc.getUniqueId());
    }

    FarmerPhase phase(UUID npcUuid) {
        FarmerRuntime runtime = runtimes.get(npcUuid);
        return runtime == null ? null : runtime.phase();
    }

    List<NPC> npcs() {
        return definitions.keySet().stream()
                .map(uuid -> CitizensAPI.getNPCRegistry().getByUniqueId(uuid))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    List<NPC> npcs(String villageId) {
        return definitions.values().stream()
                .filter(definition -> java.util.Objects.equals(definition.villageId(), villageId))
                .map(definition -> CitizensAPI.getNPCRegistry().getByUniqueId(definition.npcUuid()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    boolean ready(UUID npcUuid) {
        return readiness(npcUuid).startsWith("SẴN SÀNG");
    }

    String readiness(UUID npcUuid) {
        FarmerDefinition definition = definitions.get(npcUuid);
        if (definition == null) return "NPC chưa được quản lý";
        if (definition.villageId() == null || villageStore.get(definition.villageId()) == null) return "Chưa thuộc làng";
        if (definition.plot() == null) return "Chưa gán khu ruộng";
        if (villageStore.configuredDeliveryChest(definition.villageId()) == null) return "Làng chưa gán rương kho";
        VillageDefinition village = villageStore.get(definition.villageId());
        StoredLocation chest = villageStore.configuredDeliveryChest(definition.villageId());
        if (!sameWorld(definition, village, chest)) return "Nhà, ruộng và kho phải cùng world với làng";
        if (!definition.enabled(BehaviorFlag.MASTER)) return "Trí tuệ NPC đang tắt";
        if (!definition.enabled(BehaviorFlag.HARVEST)) return "Chưa bật Thu hoạch";
        if (!definition.enabled(BehaviorFlag.PLANT)) return "Chưa bật Trồng cây";
        if (definition.activeRole() != ResidentRole.FARMER) return "Nghề đang chọn không phải Nông dân";
        NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(npcUuid);
        if (npc == null) return "UUID NPC không còn tồn tại trong Citizens";
        if (!npc.isSpawned()) return "NPC Citizens chưa spawn";
        Location plot = definition.plot().resolve();
        if (plot == null) return "World của khu ruộng chưa được load";
        ResidentSchedule schedule = definition.schedule(
                ResidentRole.FARMER, new ResidentSchedule(config.workStartTick(), config.workEndTick()));
        if (definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                && !SchedulePolicy.isScheduledTime(plot.getWorld().getTime(), schedule)) return "Ngoài ca Nông dân";
        if (plot.getWorld().hasStorm()) return "Trời đang mưa";
        if (plot.getWorld().getNearbyPlayers(plot, config.activationRange()).isEmpty()
                && npc.getEntity().getWorld().getNearbyPlayers(
                        npc.getEntity().getLocation(), config.activationRange()).isEmpty()) {
            return "Không có người chơi trong " + (int) config.activationRange() + " block quanh NPC hoặc ruộng";
        }
        FarmerRuntime runtime = runtimes.get(npcUuid);
        return "SẴN SÀNG - phase " + (runtime == null ? FarmerPhase.INACTIVE : runtime.phase());
    }

    String activeRoleReadiness(UUID npcUuid) {
        FarmerDefinition definition = definitions.get(npcUuid);
        if (definition == null) return "NPC chưa được quản lý";
        if (definition.activeRole() == ResidentRole.FARMER) return readiness(npcUuid);
        if (definition.activeRole() == ResidentRole.MERCHANT) {
            VillageDefinition village = villageStore.get(definition.villageId());
            MerchantStall stall = village == null ? null : village.merchantStall(npcUuid);
            if (village == null) return "Chưa thuộc làng";
            if (stall == null || stall.sellerPoint() == null) return "Chưa đặt điểm đứng Dân buôn";
            if (stall.buyerPoint() == null) return "Chưa đặt điểm người mua";
            if (!stall.complete()) return "Hai điểm của quầy phải cùng world";
            if (!definition.enabled(BehaviorFlag.MASTER)) return "Trí tuệ NPC đang tắt";
            return "SẴN SÀNG - quầy Dân buôn đã gán";
        }
        VillageWorkZoneType zoneType = CivilProfessionRuntime.zoneFor(definition.activeRole());
        if (zoneType == null) return definition.enabled(BehaviorFlag.MASTER)
                ? "SẴN SÀNG - nghề đang hoạt động" : "Trí tuệ NPC đang tắt";
        VillageDefinition village = villageStore.get(definition.villageId());
        if (village == null) return "Chưa thuộc làng";
        StoredLocation stored = village.workZone(zoneType);
        if (stored == null) return "Làng chưa đặt khu nghề " + zoneType.storageKey();
        Location center = stored.resolve();
        if (center == null) return "World của khu nghề chưa được load";
        if (!definition.enabled(BehaviorFlag.MASTER)) return "Trí tuệ NPC đang tắt";
        NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(npcUuid);
        if (npc == null) return "UUID NPC không còn tồn tại trong Citizens";
        if (!npc.isSpawned()) return "NPC Citizens chưa spawn";
        if (!npc.getEntity().getWorld().equals(center.getWorld())) return "NPC và khu nghề phải cùng world";
        WorkZoneValidation validation = WorkZoneValidator.validate(
                center, zoneType, config.workZoneValidationRadius(), config.workZoneValidationVerticalRange());
        if (!validation.valid()) return "Khu nghề thiếu trạm: " + validation.missing();
        if (definition.activeRole() == ResidentRole.MINER && !mutationPolicy.hasMiningRegion(center)) {
            return "Khu mỏ chưa nằm trong region WorldGuard hợp lệ";
        }
        ResidentSchedule schedule = definition.schedule(
                definition.activeRole(), new ResidentSchedule(config.workStartTick(), config.workEndTick()));
        if (definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                && !SchedulePolicy.isWorkTime(center.getWorld().getTime(), center.getWorld().hasStorm(), schedule)) {
            return center.getWorld().hasStorm() ? "Trời đang mưa" : "Ngoài ca " + definition.activeRole().storageKey();
        }
        if (center.getWorld().getNearbyPlayers(center, config.activationRange()).isEmpty()) {
            return "Không có người chơi trong " + (int) config.activationRange() + " block quanh khu nghề";
        }
        return "SẴN SÀNG - nghề " + definition.activeRole().storageKey();
    }

    java.util.Set<String> usedProfileIds() {
        return definitions.values().stream()
                .map(definition -> definition.profile().id().toLowerCase(java.util.Locale.ROOT))
                .collect(Collectors.toSet());
    }

    NPC npc(UUID uuid) {
        return definitions.containsKey(uuid) ? CitizensAPI.getNPCRegistry().getByUniqueId(uuid) : null;
    }

    FarmerDefinition get(UUID uuid) {
        return definitions.get(uuid);
    }

    java.util.Collection<FarmerDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    boolean toggle(UUID uuid, BehaviorFlag behavior) {
        FarmerDefinition current = definitions.get(uuid);
        if (current == null) {
            return false;
        }
        return update(current.withBehavior(behavior, !current.enabled(behavior)));
    }

    boolean farmerEnabled(UUID uuid) {
        FarmerDefinition definition = definitions.get(uuid);
        return definition != null
                && definition.enabled(BehaviorFlag.MASTER)
                && definition.enabled(BehaviorFlag.HARVEST)
                && definition.enabled(BehaviorFlag.PLANT);
    }

    boolean setFarmerEnabled(UUID uuid, boolean enabled) {
        FarmerDefinition current = definitions.get(uuid);
        if (current == null || enabled && !farmerConfigured(current)) {
            return false;
        }
        return update(current.withFarmerEnabled(enabled));
    }

    String farmerSetup(UUID uuid) {
        FarmerDefinition definition = definitions.get(uuid);
        if (definition == null) return "NPC chưa được quản lý";
        VillageDefinition village = villageStore.get(definition.villageId());
        if (village == null) return "Chưa thuộc làng";
        if (definition.plot() == null) return "Chưa đặt ruộng";
        StoredLocation chest = villageStore.configuredDeliveryChest(village.id());
        if (chest == null) return "Chưa đặt kho làng";
        if (!sameWorld(definition, village, chest)) return "Nhà, ruộng và kho khác world";
        return "Đã đủ nhà, ruộng và kho";
    }

    boolean setSchedule(UUID uuid, ResidentRole role, ResidentSchedule schedule) {
        FarmerDefinition current = definitions.get(uuid);
        if (current == null || !current.profile().hasRole(role)) {
            return false;
        }
        return update(current.withSchedule(role, schedule));
    }

    boolean selectJob(UUID uuid, ResidentRole role) {
        FarmerDefinition current = definitions.get(uuid);
        if (current == null || role == null
                || !role.implemented()) {
            return false;
        }
        VillageDefinition village = villageStore.get(current.villageId());
        if (role == ResidentRole.RANCHER
                && (village == null || village.workZone(VillageWorkZoneType.RANCH) == null)) return false;
        if (role == ResidentRole.FISHER
                && (village == null || village.workZone(VillageWorkZoneType.FISHING) == null)) return false;
        VillageWorkZoneType civilZone = CivilProfessionRuntime.zoneFor(role);
        if (civilZone != null && (village == null || village.workZone(civilZone) == null)) return false;
        FarmerDefinition updated = current.withActiveRole(role);
        if (role == ResidentRole.FARMER) {
            EnumSet<BehaviorFlag> behaviors = updated.behaviors();
            behaviors.add(BehaviorFlag.HARVEST);
            behaviors.add(BehaviorFlag.PLANT);
            updated = new FarmerDefinition(
                    updated.npcUuid(), updated.villageId(), updated.home(), updated.plot(), updated.plotRadius(),
                    updated.profile(), updated.activeRole(), updated.progress(), updated.schedules(), behaviors);
        }
        return update(updated);
    }

    boolean npcActive(UUID uuid) {
        FarmerDefinition definition = definitions.get(uuid);
        return definition != null && definition.enabled(BehaviorFlag.MASTER);
    }

    boolean setNpcActive(UUID uuid, boolean active) {
        FarmerDefinition current = definitions.get(uuid);
        return current != null && update(current.withBehavior(BehaviorFlag.MASTER, active));
    }

    boolean roleActive(UUID uuid) {
        FarmerDefinition definition = definitions.get(uuid);
        if (definition == null || !definition.enabled(BehaviorFlag.MASTER)) return false;
        return definition.activeRole() != ResidentRole.FARMER
                || definition.enabled(BehaviorFlag.HARVEST) && definition.enabled(BehaviorFlag.PLANT);
    }

    boolean setRoleActive(UUID uuid, boolean active) {
        FarmerDefinition current = definitions.get(uuid);
        if (current == null || active && current.activeRole() == ResidentRole.FARMER && !farmerConfigured(current)) {
            return false;
        }
        EnumSet<BehaviorFlag> behaviors = current.behaviors();
        if (active) {
            behaviors.add(BehaviorFlag.MASTER);
        } else {
            behaviors.remove(BehaviorFlag.MASTER);
        }
        if (current.activeRole() == ResidentRole.FARMER) {
            if (active) {
                behaviors.add(BehaviorFlag.HARVEST);
                behaviors.add(BehaviorFlag.PLANT);
            } else {
                behaviors.remove(BehaviorFlag.HARVEST);
                behaviors.remove(BehaviorFlag.PLANT);
            }
        }
        return update(new FarmerDefinition(
                current.npcUuid(), current.villageId(), current.home(), current.plot(), current.plotRadius(),
                current.profile(), current.activeRole(), current.progress(), current.schedules(), behaviors));
    }

    boolean remove(int npcId) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        return npc != null && remove(npc.getUniqueId());
    }

    boolean remove(UUID npcUuid) {
        NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(npcUuid);
        FarmerDefinition removed = definitions.remove(npcUuid);
        if (removed == null) {
            return false;
        }
        FarmerRuntime runtime = runtimes.remove(npcUuid);
        if (runtime != null) runtime.suspend();
        if (!save()) {
            definitions.put(npcUuid, removed);
            if (npc != null) {
                runtimes.put(npcUuid, createRuntime(npc, removed));
            }
            return false;
        }
        if (removed.villageId() != null) villageStore.removeMerchantStall(removed.villageId(), npcUuid);
        economy.remove(npcUuid);
        if (npc != null) {
            npc.destroy();
        }
        return true;
    }

    void tick(long serverTick) {
        bindLoadedNpcs();
        pathCache.tick(serverTick, config);
        rolesChangedThisTick = Set.of();
        selectActiveRoles(serverTick);
        scheduleSocial(serverTick);
        for (FarmerRuntime runtime : runtimes.values()) {
            FarmerDefinition definition = definitions.get(runtime.npcUuid());
            if (rolesChangedThisTick.contains(runtime.npcUuid())) continue;
            boolean sleeping = !externallyBusy.contains(runtime.npcUuid()) && runtime.tickSleep(serverTick, config);
            if (!sleeping && !externallyBusy.contains(runtime.npcUuid())
                    && definition != null && definition.activeRole() != ResidentRole.RANCHER
                    && definition.activeRole() != ResidentRole.FISHER
                    && definition.activeRole() != ResidentRole.MERCHANT
                    && CivilProfessionRuntime.zoneFor(definition.activeRole()) == null) {
                runtime.tick(serverTick, config);
            }
        }
        if (progressDirty && serverTick % 1200L == 0L) {
            progressDirty = !save();
        }
    }

    boolean sleeping(UUID npcUuid) {
        FarmerRuntime runtime = runtimes.get(npcUuid);
        return runtime != null && runtime.sleeping();
    }

    boolean roleChangedThisTick(UUID npcUuid) {
        return rolesChangedThisTick.contains(npcUuid);
    }

    void setExternallyBusy(Set<UUID> npcUuids) {
        Set<UUID> updated = Set.copyOf(npcUuids);
        for (UUID npcUuid : updated) {
            if (!externallyBusy.contains(npcUuid)) {
                FarmerRuntime runtime = runtimes.get(npcUuid);
                if (runtime != null) runtime.suspend();
            }
        }
        externallyBusy = updated;
    }

    void setConfig(LivingNpcConfig config) {
        this.config = config;
        economy.setConfig(config);
        clampPlotRadii();
    }

    boolean save() {
        return store.save(definitions);
    }

    void shutdown() {
        for (FarmerRuntime runtime : runtimes.values()) {
            runtime.suspend();
        }
        seatManager.shutdown(npcs());
        save();
    }

    private boolean update(FarmerDefinition definition) {
        FarmerDefinition previous = definitions.put(definition.npcUuid(), definition);
        if (!save()) {
            if (previous == null) {
                definitions.remove(definition.npcUuid());
            } else {
                definitions.put(previous.npcUuid(), previous);
            }
            return false;
        }
        FarmerRuntime runtime = runtimes.get(definition.npcUuid());
        if (runtime != null) {
            runtime.updateDefinition(definition);
        }
        return true;
    }

    private void bindLoadedNpcs() {
        boolean migrated = false;
        for (FarmerDefinition loaded : List.copyOf(definitions.values())) {
            if (runtimes.containsKey(loaded.npcUuid())) {
                continue;
            }
            FarmerDefinition definition = loaded;
            int boundedRadius = Math.clamp(definition.plotRadius(), 1, config.maxPlotRadius());
            if (boundedRadius != definition.plotRadius()) {
                definition = definition.withPlot(definition.plot(), boundedRadius);
                definitions.put(definition.npcUuid(), definition);
                migrated = true;
            }
            NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(definition.npcUuid());
            if (npc == null) {
                continue;
            }
            configureWorkerNpc(npc);
            if (definition.profile().id().equals("custom") && definition.profile().name().equals("Resident")) {
                definition = definition.withProfile(ResidentProfile.custom(npc.getName()));
                definitions.put(definition.npcUuid(), definition);
                migrated = true;
            }
            runtimes.put(definition.npcUuid(), createRuntime(npc, definition));
        }
        if (migrated) {
            save();
        }
    }

    private void clampPlotRadii() {
        boolean changed = false;
        for (FarmerDefinition definition : List.copyOf(definitions.values())) {
            int radius = Math.clamp(definition.plotRadius(), 1, config.maxPlotRadius());
            if (radius == definition.plotRadius()) {
                continue;
            }
            FarmerDefinition bounded = definition.withPlot(definition.plot(), radius);
            definitions.put(bounded.npcUuid(), bounded);
            FarmerRuntime runtime = runtimes.get(bounded.npcUuid());
            if (runtime != null) {
                runtime.updateDefinition(bounded);
            }
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    void awardExperience(UUID npcUuid, ResidentRole role, long amount) {
        FarmerDefinition current = definitions.get(npcUuid);
        if (current == null || !current.profile().hasRole(role) || amount <= 0L) {
            return;
        }
        FarmerDefinition updated = current.withProgress(role, current.progress(role).add(amount));
        definitions.put(npcUuid, updated);
        FarmerRuntime runtime = runtimes.get(npcUuid);
        if (runtime != null) {
            runtime.refreshDefinition(updated);
        }
        progressDirty = true;
    }

    private FarmerRuntime createRuntime(NPC npc, FarmerDefinition definition) {
        return new FarmerRuntime(
                npc, definition, economy, mutationPolicy, villageStore, pathCache,
                seatManager,
                amount -> awardExperience(npc.getUniqueId(), ResidentRole.FARMER, amount));
    }

    private void configureWorkerNpc(NPC npc) {
        npc.setProtected(false);
        npc.data().remove(NPC.Metadata.RESET_PITCH_ON_TICK);
    }

    private void selectActiveRoles(long serverTick) {
        if (serverTick < nextRoleSaveAttemptTick) {
            return;
        }
        Map<UUID, FarmerDefinition> previous = new LinkedHashMap<>();
        ResidentSchedule fallback = new ResidentSchedule(config.workStartTick(), config.workEndTick());
        for (Map.Entry<UUID, FarmerRuntime> entry : runtimes.entrySet()) {
            UUID npcUuid = entry.getKey();
            FarmerDefinition current = definitions.get(npcUuid);
            NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(npcUuid);
            if (current == null || npc == null || !npc.isSpawned()
                    || !current.enabled(BehaviorFlag.FOLLOW_SCHEDULE)) {
                continue;
            }
            ResidentRole selected = ActiveRolePolicy.select(
                    current.profile().roles(), current.activeRole(), current.schedules(), fallback,
                    npc.getEntity().getWorld().getTime());
            if (selected == current.activeRole()) {
                continue;
            }
            if (current.activeRole() == ResidentRole.FARMER) {
                entry.getValue().finishFarmerShift(config);
            }
            FarmerDefinition updated = current.withActiveRole(selected);
            previous.put(npcUuid, current);
            definitions.put(npcUuid, updated);
            entry.getValue().updateDefinition(updated);
        }
        if (previous.isEmpty() || save()) {
            rolesChangedThisTick = Set.copyOf(previous.keySet());
            return;
        }
        for (Map.Entry<UUID, FarmerDefinition> entry : previous.entrySet()) {
            definitions.put(entry.getKey(), entry.getValue());
            FarmerRuntime runtime = runtimes.get(entry.getKey());
            if (runtime != null) {
                runtime.updateDefinition(entry.getValue());
            }
        }
        nextRoleSaveAttemptTick = serverTick + 1200L;
    }


    private void scheduleSocial(long serverTick) {
        if (serverTick < nextSocialTick) {
            return;
        }
        nextSocialTick = serverTick + 400L;
        for (VillageDefinition village : villageStore.villages()) {
            List<Map.Entry<UUID, FarmerRuntime>> available = runtimes.entrySet().stream()
                    .filter(entry -> {
                        FarmerDefinition definition = definitions.get(entry.getKey());
                        return definition != null && village.id().equals(definition.villageId())
                                && !externallyBusy.contains(entry.getKey())
                                && entry.getValue().availableForSocial(config);
                    })
                    .limit(2)
                    .toList();
            if (available.size() < 2) {
                continue;
            }
            boolean preferMarket = Math.floorDiv(serverTick, 400L) % 2L == 0L;
            String type = preferMarket && village.marketPoint() != null
                    ? "cho"
                    : village.scenicPoint() != null
                            ? "ngamcanh"
                            : village.marketPoint() != null ? "cho" : null;
            Location point = type == null ? null : villageStore.socialPoint(village.id(), type);
            if (point == null) {
                continue;
            }
            Map.Entry<UUID, FarmerRuntime> first = available.get(0);
            Map.Entry<UUID, FarmerRuntime> second = available.get(1);
            if (first.getValue().startSocial(serverTick, config, type, point, second.getKey())) {
                second.getValue().startSocial(serverTick, config, type, point, first.getKey());
            }
        }
    }

    private boolean matchesVillageWorld(FarmerDefinition definition, Location location) {
        VillageDefinition village = villageStore.get(definition.villageId());
        return village == null || village.center().world().equals(location.getWorld().getName());
    }

    private boolean sameWorld(FarmerDefinition definition, VillageDefinition village, StoredLocation chest) {
        if (village == null || chest == null || definition.plot() == null) {
            return false;
        }
        String world = village.center().world();
        return definition.home().world().equals(world)
                && definition.plot().world().equals(world)
                && chest.world().equals(world);
    }

    private boolean farmerConfigured(FarmerDefinition definition) {
        VillageDefinition village = villageStore.get(definition.villageId());
        StoredLocation chest = village == null ? null : villageStore.configuredDeliveryChest(village.id());
        return definition.plot() != null && sameWorld(definition, village, chest);
    }
}
