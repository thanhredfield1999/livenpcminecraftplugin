package vn.heomc.livingnpc;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record NpcTelemetryAccount(
        long balanceMinor, String currencyUnit, int inventoryTotal, List<NpcTelemetryInventoryItem> inventory) {
    static final int MAX_INVENTORY = 32;

    public NpcTelemetryAccount {
        currencyUnit = currencyUnit == null ? "minor" : currencyUnit.substring(0, Math.min(currencyUnit.length(), 32));
        inventoryTotal = Math.max(0, inventoryTotal);
        inventory = inventory == null ? List.of() : inventory.stream()
                .filter(java.util.Objects::nonNull)
                .limit(MAX_INVENTORY)
                .toList();
    }

    static NpcTelemetryAccount from(NpcAccount account) {
        if (account == null) return null;
        List<NpcTelemetryInventoryItem> inventory = account.inventory().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsFirst(String::compareTo)))
                .map(entry -> new NpcTelemetryInventoryItem(entry.getKey(), entry.getValue()))
                .toList();
        int total = account.inventory().values().stream().reduce(0, NpcTelemetryAccount::addSaturated);
        return new NpcTelemetryAccount(account.balanceMinor(), "minor", total, inventory);
    }

    private static int addSaturated(int total, int amount) {
        return (int) Math.min(Integer.MAX_VALUE, (long) total + Math.max(0, amount));
    }
}