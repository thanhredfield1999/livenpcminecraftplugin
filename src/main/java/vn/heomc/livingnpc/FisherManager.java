package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

final class FisherManager {
    private final FarmerManager residents;
    private final NpcEconomy economy;
    private final VillageStore villages;
    private final Map<UUID, FisherRuntime> runtimes = new HashMap<>();

    FisherManager(FarmerManager residents, NpcEconomy economy, VillageStore villages) {
        this.residents = residents;
        this.economy = economy;
        this.villages = villages;
    }

    void tick(long serverTick, LivingNpcConfig config) {
        java.util.Collection<FarmerDefinition> definitions = residents.definitions();
        java.util.Set<UUID> current = definitions.stream().map(FarmerDefinition::npcUuid)
                .filter(uuid -> CitizensAPI.getNPCRegistry().getByUniqueId(uuid) != null)
                .collect(java.util.stream.Collectors.toSet());
        runtimes.entrySet().removeIf(entry -> {
            if (current.contains(entry.getKey())) return false;
            entry.getValue().suspend();
            return true;
        });
        for (FarmerDefinition definition : definitions) {
            NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(definition.npcUuid());
            if (npc == null) continue;
            FisherRuntime runtime = runtimes.computeIfAbsent(definition.npcUuid(), ignored ->
                    new FisherRuntime(npc, definition, economy, villages,
                            amount -> residents.awardExperience(definition.npcUuid(), ResidentRole.FISHER, amount)));
            runtime.updateDefinition(definition);
            if (residents.roleChangedThisTick(definition.npcUuid())) continue;
            if (residents.sleeping(definition.npcUuid())) {
                runtime.releaseWorkState();
                continue;
            }
            if (definition.activeRole() == ResidentRole.FISHER) runtime.tick(serverTick, config);
        }
    }

    FarmerPhase phase(UUID npcUuid) {
        FisherRuntime runtime = runtimes.get(npcUuid);
        return runtime == null ? null : runtime.phase();
    }

    void shutdown() {
        runtimes.values().forEach(FisherRuntime::suspend);
        runtimes.clear();
    }
}
