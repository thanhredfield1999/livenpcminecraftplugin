package vn.heomc.livingnpc;

import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

final class NpcEconomy {
    private static final UUID TOWN_ACCOUNT_UUID = new UUID(0L, 0L);
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
        if (amount <= 0) {
            return false;
        }
        NpcAccount account = store.account(npcUuid);
        NpcAccount town = villageAccount(villageId);
        resetShiftIfNeeded(account, shiftKey);
        int allowedByInventory = config.inventoryCapacity() - town.inventorySize();
        int allowedByQuota = config.maxOutputPerShift() - account.producedThisShift();
        int accepted = Math.min(amount, Math.min(allowedByInventory, allowedByQuota));
        if (accepted <= 0) {
            return false;
        }
        town.setQuantity(itemKey, town.quantity(itemKey) + accepted);
        account.setProducedThisShift(account.producedThisShift() + accepted);
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
        return town.inventorySize() + amount <= config.inventoryCapacity()
                && account.producedThisShift() + amount <= config.maxOutputPerShift();
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
        }
    }

}
