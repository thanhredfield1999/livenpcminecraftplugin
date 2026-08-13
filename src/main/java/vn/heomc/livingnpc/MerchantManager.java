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
    private final Map<UUID, String> reservations = new HashMap<>();

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
            return true;
        });
        for (FarmerDefinition definition : residents.definitions()) {
            if (definition.activeRole() != ResidentRole.MERCHANT) {
                MerchantRuntime removed = runtimes.remove(definition.npcUuid());
                if (removed != null) removed.suspend();
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

    MerchantStall reserveOpenStall(String villageId, String visitId) {
        VillageDefinition village = villages.get(villageId);
        if (village == null || visitId == null || visitId.isBlank()) return null;
        for (MerchantStall stall : village.merchantStalls()) {
            MerchantRuntime runtime = runtimes.get(stall.merchantUuid());
            if (stall.complete() && runtime != null && runtime.open()
                    && reservations.putIfAbsent(stall.merchantUuid(), visitId) == null) return stall;
        }
        return null;
    }

    void release(UUID merchantUuid, String visitId) {
        if (merchantUuid != null && visitId != null) reservations.remove(merchantUuid, visitId);
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
        reservations.clear();
    }
}
