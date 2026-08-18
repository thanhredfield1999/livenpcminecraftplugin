package vn.heomc.livingnpc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class NpcTelemetrySnapshotCapture {
    private NpcTelemetrySnapshotCapture() {
    }

    static NpcTelemetryEconomySnapshot economy(
            VillageStore villages, FarmerManager residents, NpcEconomy economy) {
        Map<UUID, NpcAccount> accounts = accountsByUuid(economy);
        List<NpcTelemetryVillageEconomy> result = new ArrayList<>();
        for (VillageDefinition village : villages.villages()) {
            if (result.size() >= NpcTelemetryEconomySnapshot.MAX_VILLAGES) break;
            NpcAccount account = accounts.get(economy.villageAccountUuid(village.id()));
            result.add(new NpcTelemetryVillageEconomy(
                    village.id(), account == null ? 0L : account.balanceMinor(), "minor",
                    economy.totalEarnedMinor(village.id()), economy.totalSpentMinor(village.id()),
                    inventory(account), roleProduction(village.id(), residents, accounts), activities(village.id(), economy),
                    center(village)));
        }
        return new NpcTelemetryEconomySnapshot(result);
    }

    private static NpcTelemetryPosition center(VillageDefinition village) {
        StoredLocation location = village.center();
        if (location == null) return null;
        return new NpcTelemetryPosition(location.world(), (int) Math.floor(location.x()), (int) Math.floor(location.y()),
                (int) Math.floor(location.z()), location.x(), location.y(), location.z(), location.yaw(), location.pitch());
    }

    private static Map<UUID, NpcAccount> accountsByUuid(NpcEconomy economy) {
        Map<UUID, NpcAccount> accounts = new HashMap<>();
        economy.telemetryAccounts().forEach(account -> accounts.put(account.npcUuid(), account));
        return accounts;
    }

    private static List<NpcTelemetryInventoryItem> inventory(NpcAccount account) {
        if (account == null) return List.of();
        return account.inventory().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new NpcTelemetryInventoryItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<NpcTelemetryRoleProduction> roleProduction(
            String villageId, FarmerManager residents, Map<UUID, NpcAccount> accounts) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (FarmerDefinition definition : residents.definitions()) {
            if (!villageId.equals(definition.villageId())) continue;
            NpcAccount account = accounts.get(definition.npcUuid());
            if (account == null) continue;
            account.roleProduction().forEach((role, amount) -> totals.merge(role, amount, NpcTelemetrySnapshotCapture::addBounded));
        }
        return totals.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new NpcTelemetryRoleProduction(entry.getKey(), entry.getValue())).toList();
    }

    private static int addBounded(int first, int second) {
        return (int) Math.min(Integer.MAX_VALUE, (long) first + Math.max(0, second));
    }

    private static List<NpcTelemetryActivity> activities(String villageId, NpcEconomy economy) {
        return economy.activities(villageId, null, NpcTelemetryVillageEconomy.MAX_ACTIVITIES).stream()
                .sorted(Comparator.comparing(NpcActivity::createdAt).reversed())
                .map(activity -> new NpcTelemetryActivity(
                        activity.role().storageKey(), activity.action(), activity.itemKey(), activity.amount(), activity.createdAt()))
                .toList();
    }
}