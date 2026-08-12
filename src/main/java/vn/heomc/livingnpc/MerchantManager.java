package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

final class MerchantManager {
    private final FarmerManager residents;
    private final VillageStore villages;
    private final Map<UUID, MerchantRuntime> runtimes = new HashMap<>();
    private final Set<UUID> reserved = new java.util.HashSet<>();

    MerchantManager(FarmerManager residents, VillageStore villages) {
        this.residents = residents;
        this.villages = villages;
    }

    void tick(long serverTick, LivingNpcConfig config) {
        Set<UUID> managed = residents.definitions().stream().map(FarmerDefinition::npcUuid)
                .collect(java.util.stream.Collectors.toSet());
        runtimes.entrySet().removeIf(entry -> {
            if (managed.contains(entry.getKey())) return false;
            entry.getValue().suspend();
            reserved.remove(entry.getKey());
            return true;
        });
        for (FarmerDefinition definition : residents.definitions()) {
            if (definition.activeRole() != ResidentRole.MERCHANT) {
                MerchantRuntime removed = runtimes.remove(definition.npcUuid());
                if (removed != null) removed.suspend();
                reserved.remove(definition.npcUuid());
                continue;
            }
            NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(definition.npcUuid());
            if (npc == null) continue;
            MerchantRuntime runtime = runtimes.computeIfAbsent(
                    definition.npcUuid(), ignored -> new MerchantRuntime(npc, definition));
            runtime.updateDefinition(definition);
            if (residents.roleChangedThisTick(definition.npcUuid()) || residents.sleeping(definition.npcUuid())) {
                runtime.releaseForSleep();
                continue;
            }
            runtime.tick(serverTick, config, villages.get(definition.villageId()));
        }
    }

    MerchantStall reserveOpenStall(String villageId) {
        VillageDefinition village = villages.get(villageId);
        if (village == null) return null;
        for (MerchantStall stall : village.merchantStalls()) {
            MerchantRuntime runtime = runtimes.get(stall.merchantUuid());
            if (stall.complete() && runtime != null && runtime.open() && reserved.add(stall.merchantUuid())) return stall;
        }
        return null;
    }

    void release(UUID merchantUuid) {
        reserved.remove(merchantUuid);
    }

    boolean open(UUID merchantUuid) {
        MerchantRuntime runtime = runtimes.get(merchantUuid);
        return runtime != null && runtime.open();
    }

    FarmerPhase phase(UUID merchantUuid) {
        MerchantRuntime runtime = runtimes.get(merchantUuid);
        return runtime == null ? null : runtime.phase();
    }

    void shutdown() {
        runtimes.values().forEach(MerchantRuntime::suspend);
        runtimes.clear();
        reserved.clear();
    }
}
