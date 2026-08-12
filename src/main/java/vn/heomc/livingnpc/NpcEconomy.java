package vn.heomc.livingnpc;

import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

final class NpcEconomy {
    private static final UUID TOWN_ACCOUNT_UUID = new UUID(0L, 0L);
    private static final int CARRIED_SLOTS = 2;
    private static final int STACK_SIZE = 64;
    private final NpcEconomyStore store;
    private final NpcPriceBook priceBook;
    private LivingNpcConfig config;
    private boolean dirty;

    NpcEconomy(NpcEconomyStore store, NpcPriceBook priceBook, LivingNpcConfig config) {
        this.store = store;
        this.priceBook = priceBook;
        this.config = config;
    }

    boolean addProduction(UUID npcUuid, String itemKey, int amount, long shiftKey) {
        return addProduction(npcUuid, null, itemKey, amount, shiftKey);
    }

    boolean addProduction(UUID npcUuid, String villageId, String itemKey, int amount, long shiftKey) {
        return addProduction(npcUuid, villageId, itemKey, amount, shiftKey, true);
    }

    boolean addByproduct(UUID npcUuid, String villageId, String itemKey, int amount, long shiftKey) {
        return addProduction(npcUuid, villageId, itemKey, amount, shiftKey, false);
    }

    private boolean addProduction(
            UUID npcUuid, String villageId, String itemKey, int amount, long shiftKey, boolean countsTowardQuota) {
        if (amount <= 0) {
            return false;
        }
        NpcAccount account = store.account(npcUuid);
        NpcAccount town = villageAccount(villageId);
        resetShiftIfNeeded(account, shiftKey);
        int allowedByInventory = config.unlimitedStorage()
                ? amount
                : config.inventoryCapacity() - town.inventorySize();
        int allowedByItem = Integer.MAX_VALUE - town.quantity(itemKey);
        int allowedByQuota = countsTowardQuota
                ? config.maxOutputPerShift() - account.producedThisShift()
                : amount;
        int accepted = Math.min(amount, Math.min(allowedByItem, Math.min(allowedByInventory, allowedByQuota)));
        if (accepted <= 0) {
            return false;
        }
        town.setQuantity(itemKey, town.quantity(itemKey) + accepted);
        if (countsTowardQuota) {
            account.setProducedThisShift(account.producedThisShift() + accepted);
        }
        dirty = true;
        return true;
    }

    boolean canAcceptProduction(UUID npcUuid, int amount, long shiftKey) {
        return canAcceptProduction(npcUuid, null, amount, shiftKey);
    }

    boolean canAcceptProduction(UUID npcUuid, String villageId, int amount, long shiftKey) {
        if (amount <= 0) {
            return false;
        }
        NpcAccount account = store.account(npcUuid);
        NpcAccount town = villageAccount(villageId);
        resetShiftIfNeeded(account, shiftKey);
        return hasStorageFor(town, amount)
                && account.producedThisShift() + amount <= config.maxOutputPerShift();
    }

    boolean canAcceptHarvest(UUID npcUuid, String villageId, int output, int byproduct, long shiftKey) {
        if (output <= 0 || byproduct < 0) return false;
        NpcAccount account = store.account(npcUuid);
        NpcAccount town = villageAccount(villageId);
        resetShiftIfNeeded(account, shiftKey);
        return hasStorageFor(town, (long) output + byproduct)
                && account.producedThisShift() + output <= config.maxOutputPerShift();
    }

    boolean addRoleProduction(
            UUID npcUuid, String villageId, ResidentRole role, String itemKey,
            int amount, int roleLimit, long shiftKey) {
        if (role == null || amount <= 0 || roleLimit <= 0) return false;
        NpcAccount account = store.account(npcUuid);
        NpcAccount town = villageAccount(villageId);
        resetShiftIfNeeded(account, shiftKey);
        String roleKey = role.storageKey();
        if (account.roleProduction(roleKey) + amount > roleLimit
                || account.producedThisShift() + amount > config.maxOutputPerShift()
                || !hasStorageFor(town, amount)
                || (long) town.quantity(itemKey) + amount > Integer.MAX_VALUE) return false;
        town.setQuantity(itemKey, town.quantity(itemKey) + amount);
        account.setProducedThisShift(account.producedThisShift() + amount);
        account.setRoleProduction(roleKey, account.roleProduction(roleKey) + amount);
        dirty = true;
        return true;
    }

    boolean transformVillageItems(
            UUID npcUuid, String villageId, ResidentRole role,
            Map<String, Integer> inputs, String output, int outputAmount, int roleLimit, long shiftKey) {
        if (role == null || inputs == null || output == null || output.isBlank()
                || outputAmount <= 0 || roleLimit <= 0) return false;
        NpcAccount worker = store.account(npcUuid);
        NpcAccount town = villageAccount(villageId);
        resetShiftIfNeeded(worker, shiftKey);
        long consumed = 0L;
        for (Map.Entry<String, Integer> input : inputs.entrySet()) {
            if (input.getKey() == null || input.getKey().isBlank() || input.getValue() <= 0
                    || town.quantity(input.getKey()) < input.getValue()) return false;
            consumed = Math.addExact(consumed, input.getValue());
        }
        if (worker.roleProduction(role.storageKey()) + outputAmount > roleLimit
                || worker.producedThisShift() + outputAmount > config.maxOutputPerShift()
                || !hasStorageFor(town, outputAmount - consumed)
                || (long) town.quantity(output) + outputAmount > Integer.MAX_VALUE) return false;
        inputs.forEach((key, amount) -> town.setQuantity(key, town.quantity(key) - amount));
        town.setQuantity(output, town.quantity(output) + outputAmount);
        worker.setProducedThisShift(worker.producedThisShift() + outputAmount);
        worker.setRoleProduction(role.storageKey(), worker.roleProduction(role.storageKey()) + outputAmount);
        dirty = true;
        return true;
    }

    boolean canAcceptRoleProduction(
            UUID npcUuid, String villageId, ResidentRole role, int amount, int roleLimit, long shiftKey) {
        if (role == null || amount <= 0 || roleLimit <= 0) return false;
        NpcAccount account = store.account(npcUuid);
        NpcAccount town = villageAccount(villageId);
        resetShiftIfNeeded(account, shiftKey);
        return account.roleProduction(role.storageKey()) + amount <= roleLimit
                && account.producedThisShift() + amount <= config.maxOutputPerShift()
                && hasStorageFor(town, amount);
    }

    SaleResult sellAtShiftEnd(UUID npcUuid, long completedShiftKey) {
        return sellAtShiftEnd(npcUuid, null, completedShiftKey);
    }

    SaleResult sellAtShiftEnd(UUID npcUuid, String villageId, long completedShiftKey) {
        NpcAccount account = villageAccount(villageId);
        if (account.lastSaleShift() == completedShiftKey || account.inventorySize() == 0) {
            return SaleResult.EMPTY;
        }
        long total = 0L;
        int count = 0;
        for (Map.Entry<String, Integer> item : account.inventory().entrySet()) {
            long unitPrice = priceBook.priceMinor(item.getKey());
            if (unitPrice <= 0L) {
                continue;
            }
            total = Math.addExact(total, Math.multiplyExact(unitPrice, item.getValue()));
            count += item.getValue();
        }
        if (total <= 0L) {
            return SaleResult.EMPTY;
        }
        String transactionId = "village:" + (villageId == null ? "legacy" : villageId) + ":" + completedShiftKey;
        NpcAccount previous = account.copy();
        account.setBalanceMinor(Math.addExact(account.balanceMinor(), total));
        for (String itemKey : account.inventory().keySet()) {
            if (priceBook.priceMinor(itemKey) > 0L) {
                account.setQuantity(itemKey, 0);
            }
        }
        account.setLastSaleShift(completedShiftKey);
        SaleResult sale = new SaleResult(count, total, transactionId);
        store.recordSale(account.npcUuid(), sale);
        if (!store.save()) {
            store.restore(previous);
            store.removeSale(transactionId);
            dirty = true;
            return SaleResult.EMPTY;
        }
        dirty = false;
        return sale;
    }

    NpcAccount account(UUID npcUuid) {
        return store.account(npcUuid);
    }

    NpcAccount townAccount() {
        return store.account(TOWN_ACCOUNT_UUID);
    }

    NpcAccount villageAccount(String villageId) {
        return store.account(villageAccountUuid(villageId));
    }

    void creditVillage(String villageId, long amountMinor) {
        if (amountMinor <= 0L) return;
        NpcAccount account = villageAccount(villageId);
        account.setBalanceMinor(Math.addExact(account.balanceMinor(), amountMinor));
        dirty = true;
    }

    VisitorPurchaseResult visitorPurchase(
            String villageId, String transactionId, long walletMinor, int maxItems) {
        if (walletMinor <= 0L || maxItems <= 0 || store.hasTransaction(transactionId)) {
            return VisitorPurchaseResult.empty(walletMinor);
        }
        NpcAccount account = villageAccount(villageId);
        NpcAccount previous = account.copy();
        java.util.LinkedHashMap<String, Integer> purchased = new java.util.LinkedHashMap<>();
        long remaining = walletMinor;
        long spent = 0L;
        int remainingItems = maxItems;
        for (Map.Entry<String, Integer> item : account.inventory().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (remainingItems <= 0) break;
            long price = priceBook.priceMinor(item.getKey());
            if (price <= 0L || price > remaining || item.getValue() <= 0) continue;
            int quantity = (int) Math.min(remainingItems,
                    Math.min(item.getValue(), Math.min(3L, remaining / price)));
            if (quantity <= 0) continue;
            long cost = Math.multiplyExact(price, quantity);
            account.setQuantity(item.getKey(), item.getValue() - quantity);
            purchased.put(item.getKey(), quantity);
            spent = Math.addExact(spent, cost);
            remaining -= cost;
            remainingItems -= quantity;
        }
        if (purchased.isEmpty()) return VisitorPurchaseResult.empty(walletMinor);
        account.setBalanceMinor(Math.addExact(account.balanceMinor(), spent));
        store.recordVisitorSale(account.npcUuid(), transactionId,
                purchased.values().stream().mapToInt(Integer::intValue).sum(), spent);
        if (!store.save()) {
            store.restore(previous);
            store.removeSale(transactionId);
            return VisitorPurchaseResult.empty(walletMinor);
        }
        dirty = false;
        return new VisitorPurchaseResult(purchased, spent, remaining);
    }

    boolean consumeVillageItem(String villageId, String itemKey, int amount) {
        if (itemKey == null || itemKey.isBlank() || amount <= 0) return false;
        NpcAccount account = villageAccount(villageId);
        if (account.quantity(itemKey) < amount) return false;
        account.setQuantity(itemKey, account.quantity(itemKey) - amount);
        dirty = true;
        return true;
    }

    boolean canStoreVillageItems(String villageId, int amount) {
        return amount >= 0 && hasStorageFor(villageAccount(villageId), amount);
    }

    boolean addVillageLoot(String villageId, Map<String, Integer> loot) {
        int total = loot.values().stream().filter(amount -> amount > 0).mapToInt(Integer::intValue).sum();
        if (total <= 0 || !canStoreVillageItems(villageId, total)) return false;
        NpcAccount account = villageAccount(villageId);
        for (Map.Entry<String, Integer> item : loot.entrySet()) {
            if (item.getKey() != null && !item.getKey().isBlank() && item.getValue() > 0) {
                account.setQuantity(item.getKey(), account.quantity(item.getKey()) + item.getValue());
            }
        }
        dirty = true;
        return true;
    }

    boolean addCarriedLoot(UUID npcUuid, Map<String, Integer> loot) {
        if (npcUuid == null || loot == null || loot.isEmpty()) return false;
        NpcAccount carried = account(npcUuid);
        Map<String, Integer> updated = new java.util.LinkedHashMap<>(carried.inventory());
        for (Map.Entry<String, Integer> item : loot.entrySet()) {
            if (item.getKey() == null || item.getKey().isBlank() || item.getValue() <= 0) continue;
            int current = updated.getOrDefault(item.getKey(), 0);
            updated.put(item.getKey(), current + item.getValue());
            if (usedCarriedSlots(updated) > CARRIED_SLOTS) return false;
        }
        updated.forEach(carried::setQuantity);
        dirty = true;
        return true;
    }

    boolean carriedInventoryFull(UUID npcUuid) {
        NpcAccount carried = account(npcUuid);
        return usedCarriedSlots(carried.inventory()) >= CARRIED_SLOTS;
    }

    boolean depositCarriedLoot(UUID npcUuid, String villageId) {
        NpcAccount carried = account(npcUuid);
        Map<String, Integer> items = carried.inventory();
        if (items.isEmpty()) return true;
        int total = items.values().stream().mapToInt(Integer::intValue).sum();
        if (!canStoreVillageItems(villageId, total)) return false;
        NpcAccount village = villageAccount(villageId);
        items.forEach((key, amount) -> village.setQuantity(key, village.quantity(key) + amount));
        carried.clearInventory();
        dirty = true;
        return true;
    }

    private int usedCarriedSlots(Map<String, Integer> inventory) {
        return inventory.values().stream().filter(amount -> amount > 0)
                .mapToInt(amount -> (amount + STACK_SIZE - 1) / STACK_SIZE).sum();
    }

    private boolean hasStorageFor(NpcAccount account, long amount) {
        if (amount <= 0L || config.unlimitedStorage()) return true;
        return (long) account.inventorySize() + amount <= config.inventoryCapacity();
    }

    void recordActivity(UUID npcUuid, String villageId, ResidentRole role, String action, String itemKey, int amount) {
        store.recordActivity(new NpcActivity(npcUuid, villageId, role, action, itemKey, amount, Instant.now()));
        dirty = true;
    }

    java.util.List<NpcActivity> activities(String villageId, ResidentRole role, int limit) {
        return store.activities(villageId, role, Math.clamp(limit, 1, 10));
    }

    NpcActivity latestActivity(UUID npcUuid) {
        return store.latestActivity(npcUuid);
    }

    private UUID villageAccountUuid(String villageId) {
        if (villageId == null || villageId.isBlank()) {
            return TOWN_ACCOUNT_UUID;
        }
        return UUID.nameUUIDFromBytes(("livingnpc:village:" + villageId).getBytes(StandardCharsets.UTF_8));
    }

    boolean remove(UUID npcUuid) {
        return store.remove(npcUuid);
    }

    void setConfig(LivingNpcConfig config) {
        this.config = config;
    }

    void reloadPrices(java.io.File dataFolder) {
        priceBook.reload(dataFolder);
    }

    void flush() {
        if (dirty) {
            dirty = !store.save();
        }
    }

    private void resetShiftIfNeeded(NpcAccount account, long shiftKey) {
        if (account.shiftKey() != shiftKey) {
            account.setShiftKey(shiftKey);
            account.setProducedThisShift(0);
            account.clearRoleProduction();
            dirty = true;
        }
    }

}
