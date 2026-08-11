package vn.heomc.livingnpc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

final class FarmerManager {
    private final FarmerStore store;
    private final Map<UUID, FarmerDefinition> definitions;
    private final Map<UUID, FarmerRuntime> runtimes = new LinkedHashMap<>();
    private final NpcEconomy economy;
    private final WorldMutationPolicy mutationPolicy;
    private final VillageStore villageStore;
    private LivingNpcConfig config;
    private boolean progressDirty;
    private long nextRoleSaveAttemptTick;
    private long nextSocialTick;
    private Set<UUID> externallyBusy = Set.of();

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
        this.config = config;
        this.definitions = store.load();
        bindLoadedNpcs();
    }

    NPC create(ResidentProfile profile, Location home) {
        return create(profile, home, null);
    }

    NPC create(ResidentProfile profile, Location home, String villageId) {
        if (!profile.hasRole(ResidentRole.FARMER)) {
            return null;
        }
        if (villageId != null && villageStore.get(villageId) == null) {
            return null;
        }
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, profile.name());
        if (!profile.skin().isBlank()) {
            npc.getOrAddTrait(SkinTrait.class).setSkinName(profile.skin(), true);
        }
        FarmerDefinition definition = new FarmerDefinition(
                npc.getUniqueId(), villageId, StoredLocation.from(home), null, 4, profile, BehaviorFlag.safeDefaults());
        definitions.put(npc.getUniqueId(), definition);
        runtimes.put(npc.getUniqueId(), createRuntime(npc, definition));
        if (!npc.spawn(home)) {
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
        if (current == null) {
            return false;
        }
        return update(current.withHome(StoredLocation.from(home)));
    }

    boolean setPlot(int npcId, Location plot, int radius) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        return npc != null && setPlot(npc.getUniqueId(), plot, radius);
    }

    boolean setPlot(UUID npcUuid, Location plot, int radius) {
        FarmerDefinition current = definitions.get(npcUuid);
        if (current == null) {
            return false;
        }
        int boundedRadius = Math.clamp(radius, 1, config.maxPlotRadius());
        return update(current.withPlot(StoredLocation.from(plot), boundedRadius));
    }

    FarmerDefinition get(int npcId) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        return npc == null ? null : definitions.get(npc.getUniqueId());
    }

    FarmerPhase phase(int npcId) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        FarmerRuntime runtime = npc == null ? null : runtimes.get(npc.getUniqueId());
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
        FarmerDefinition definition = definitions.get(npcUuid);
        return definition != null
                && definition.villageId() != null
                && villageStore.deliveryChest(definition.villageId()) != null
                && definition.plot() != null
                && definition.enabled(BehaviorFlag.MASTER)
                && definition.enabled(BehaviorFlag.HARVEST)
                && definition.enabled(BehaviorFlag.PLANT);
    }

    String readiness(UUID npcUuid) {
        FarmerDefinition definition = definitions.get(npcUuid);
        if (definition == null) return "NPC chưa được quản lý";
        if (definition.villageId() == null || villageStore.get(definition.villageId()) == null) return "Chưa thuộc làng";
        if (definition.plot() == null) return "Chưa gán khu ruộng";
        if (villageStore.deliveryChest(definition.villageId()) == null) return "Làng chưa gán rương kho";
        if (!definition.enabled(BehaviorFlag.MASTER)) return "Trí tuệ NPC đang tắt";
        if (!definition.enabled(BehaviorFlag.HARVEST)) return "Chưa bật Thu hoạch";
        if (!definition.enabled(BehaviorFlag.PLANT)) return "Chưa bật Trồng cây";
        return "SẴN SÀNG - bắt đầu ở tick kế tiếp khi đúng ca";
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

    boolean toggle(UUID uuid, BehaviorFlag behavior) {
        FarmerDefinition current = definitions.get(uuid);
        if (current == null) {
            return false;
        }
        return update(current.withBehavior(behavior, !current.enabled(behavior)));
    }

    boolean setSchedule(UUID uuid, ResidentRole role, ResidentSchedule schedule) {
        FarmerDefinition current = definitions.get(uuid);
        if (current == null || !current.profile().hasRole(role)) {
            return false;
        }
        return update(current.withSchedule(role, schedule));
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
        runtimes.remove(npcUuid);
        if (!save()) {
            definitions.put(npcUuid, removed);
            if (npc != null) {
                runtimes.put(npcUuid, createRuntime(npc, removed));
            }
            return false;
        }
        economy.remove(npcUuid);
        if (npc != null) {
            npc.destroy();
        }
        return true;
    }

    void tick(long serverTick) {
        bindLoadedNpcs();
        selectActiveRoles(serverTick);
        scheduleSocial(serverTick);
        for (FarmerRuntime runtime : runtimes.values()) {
            if (!externallyBusy.contains(runtime.npcUuid())) {
                runtime.tick(serverTick, config);
            }
        }
        if (progressDirty && serverTick % 1200L == 0L) {
            progressDirty = !save();
        }
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
                npc, definition, economy, mutationPolicy, villageStore,
                amount -> awardExperience(npc.getUniqueId(), ResidentRole.FARMER, amount));
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
            FarmerDefinition updated = current.withActiveRole(selected);
            previous.put(npcUuid, current);
            definitions.put(npcUuid, updated);
            entry.getValue().updateDefinition(updated);
        }
        if (previous.isEmpty() || save()) {
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
}
