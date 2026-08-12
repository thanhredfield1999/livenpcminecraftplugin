package vn.heomc.livingnpc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class NpcAccount {
    private final UUID npcUuid;
    private final Map<String, Integer> inventory = new LinkedHashMap<>();
    private long balanceMinor;
    private int producedThisShift;
    private long shiftKey = Long.MIN_VALUE;
    private long lastSaleShift = Long.MIN_VALUE;
    private final Map<String, Integer> roleProduction = new LinkedHashMap<>();

    NpcAccount(UUID npcUuid) {
        this.npcUuid = npcUuid;
    }

    NpcAccount copy() {
        NpcAccount copy = new NpcAccount(npcUuid);
        copy.balanceMinor = balanceMinor;
        copy.inventory.putAll(inventory);
        copy.producedThisShift = producedThisShift;
        copy.shiftKey = shiftKey;
        copy.lastSaleShift = lastSaleShift;
        copy.roleProduction.putAll(roleProduction);
        return copy;
    }

    UUID npcUuid() {
        return npcUuid;
    }

    long balanceMinor() {
        return balanceMinor;
    }

    void setBalanceMinor(long balanceMinor) {
        this.balanceMinor = Math.max(0L, balanceMinor);
    }

    Map<String, Integer> inventory() {
        return Map.copyOf(inventory);
    }

    int inventorySize() {
        return inventory.values().stream().mapToInt(Integer::intValue).sum();
    }

    int quantity(String itemKey) {
        return inventory.getOrDefault(itemKey, 0);
    }

    void setQuantity(String itemKey, int quantity) {
        if (quantity <= 0) {
            inventory.remove(itemKey);
        } else {
            inventory.put(itemKey, quantity);
        }
    }

    void clearInventory() {
        inventory.clear();
    }

    int producedThisShift() {
        return producedThisShift;
    }

    void setProducedThisShift(int producedThisShift) {
        this.producedThisShift = Math.max(0, producedThisShift);
    }

    long shiftKey() {
        return shiftKey;
    }

    void setShiftKey(long shiftKey) {
        this.shiftKey = shiftKey;
    }

    int roleProduction(String role) {
        return roleProduction.getOrDefault(role, 0);
    }

    void setRoleProduction(String role, int amount) {
        if (amount <= 0) roleProduction.remove(role);
        else roleProduction.put(role, amount);
    }

    Map<String, Integer> roleProduction() {
        return Map.copyOf(roleProduction);
    }

    void clearRoleProduction() {
        roleProduction.clear();
    }

    long lastSaleShift() {
        return lastSaleShift;
    }

    void setLastSaleShift(long lastSaleShift) {
        this.lastSaleShift = lastSaleShift;
    }
}
