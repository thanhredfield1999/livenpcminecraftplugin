package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

final class CivilProfessionManager {
    private final FarmerManager residents;
    private final NpcEconomy economy;
    private final VillageStore villages;
    private final WorldMutationPolicy mutationPolicy;
    private final Map<UUID, CivilProfessionRuntime> runtimes = new HashMap<>();

    CivilProfessionManager(
            FarmerManager residents, NpcEconomy economy, VillageStore villages,
            WorldMutationPolicy mutationPolicy) {
        this.residents = residents;
        this.economy = economy;
        this.villages = villages;
        this.mutationPolicy = mutationPolicy;
    }

    void tick(long serverTick, LivingNpcConfig config) {
        java.util.Collection<FarmerDefinition> definitions = residents.definitions();
        java.util.Set<UUID> managed = definitions.stream().map(FarmerDefinition::npcUuid)
                .filter(uuid -> CitizensAPI.getNPCRegistry().getByUniqueId(uuid) != null)
                .collect(java.util.stream.Collectors.toSet());
        runtimes.entrySet().removeIf(entry -> {
            if (managed.contains(entry.getKey())) return false;
            entry.getValue().suspend();
            return true;
        });
        for (FarmerDefinition definition : definitions) {
            NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(definition.npcUuid());
            if (npc == null) continue;
            CivilProfessionRuntime runtime = runtimes.computeIfAbsent(definition.npcUuid(), ignored ->
                    new CivilProfessionRuntime(
                            npc, definition, economy, villages, mutationPolicy,
                            amount -> {
                                FarmerDefinition current = residents.get(definition.npcUuid());
                                if (current != null) residents.awardExperience(
                                        definition.npcUuid(), current.activeRole(), amount);
                            }));
            runtime.updateDefinition(definition);
            if (residents.roleChangedThisTick(definition.npcUuid())) continue;
            if (residents.sleeping(definition.npcUuid())) {
                runtime.releaseWorkState();
                continue;
            }
            if (CivilProfessionRuntime.zoneFor(definition.activeRole()) != null) runtime.tick(serverTick, config);
        }
    }

    FarmerPhase phase(UUID npcUuid) {
        CivilProfessionRuntime runtime = runtimes.get(npcUuid);
        return runtime == null ? null : runtime.phase();
    }

    void shutdown() {
        runtimes.values().forEach(CivilProfessionRuntime::suspend);
        runtimes.clear();
    }
}
