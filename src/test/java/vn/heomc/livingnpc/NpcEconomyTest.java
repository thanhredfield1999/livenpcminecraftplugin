package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NpcEconomyTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void enforcesCapacityAndShiftQuota() throws IOException {
        NpcEconomy economy = economy(4, 3, false);
        UUID npc = UUID.randomUUID();

        assertTrue(economy.addProduction(npc, "wheat", 2, 1));
        assertTrue(economy.addProduction(npc, "wheat", 2, 1));
        assertFalse(economy.addProduction(npc, "wheat", 1, 1));
        assertEquals(3, economy.townAccount().inventorySize());
        assertEquals(3, economy.account(npc).producedThisShift());
    }

    @Test
    void unlimitedStorageIgnoresCapacityButKeepsShiftQuota() throws IOException {
        NpcEconomy economy = economy(2, 5, true);
        UUID npc = UUID.randomUUID();

        assertTrue(economy.addProduction(npc, "wheat", 5, 1));
        assertEquals(5, economy.townAccount().inventorySize());
        assertFalse(economy.addProduction(npc, "wheat", 1, 1));
    }

    @Test
    void saleIsPrivateIdempotentAndKeepsUnpricedItems() throws IOException {
        NpcEconomy economy = economy(64, 32);
        UUID npc = UUID.randomUUID();
        economy.addProduction(npc, "wheat", 2, 1);
        economy.addProduction(npc, "unknown", 1, 1);

        SaleResult first = economy.sellAtShiftEnd(npc, 1);
        SaleResult duplicate = economy.sellAtShiftEnd(npc, 1);

        assertEquals(500L, first.totalMinor());
        assertEquals(500L, economy.townAccount().balanceMinor());
        assertEquals(1, economy.townAccount().quantity("unknown"));
        assertEquals(0L, duplicate.totalMinor());
    }

    @Test
    void sharesTownInventoryButKeepsPerNpcShiftQuotas() throws IOException {
        NpcEconomy economy = economy(5, 2);
        UUID firstNpc = UUID.randomUUID();
        UUID secondNpc = UUID.randomUUID();

        assertTrue(economy.addProduction(firstNpc, "wheat", 2, 1));
        assertFalse(economy.addProduction(firstNpc, "wheat", 1, 1));
        assertTrue(economy.addProduction(secondNpc, "wheat", 2, 1));

        assertEquals(4, economy.townAccount().quantity("wheat"));
        assertEquals(2, economy.account(firstNpc).producedThisShift());
        assertEquals(2, economy.account(secondNpc).producedThisShift());
    }

    @Test
    void keepsVillageStorageAndBalancesSeparate() throws IOException {
        NpcEconomy economy = economy(5, 3);
        UUID firstNpc = UUID.randomUUID();
        UUID secondNpc = UUID.randomUUID();

        economy.addProduction(firstNpc, "lang_a", "wheat", 2, 1);
        economy.addProduction(secondNpc, "lang_b", "wheat", 1, 1);
        economy.sellAtShiftEnd(firstNpc, "lang_a", 1);

        assertEquals(0, economy.villageAccount("lang_a").quantity("wheat"));
        assertEquals(500L, economy.villageAccount("lang_a").balanceMinor());
        assertEquals(1, economy.villageAccount("lang_b").quantity("wheat"));
        assertEquals(0L, economy.villageAccount("lang_b").balanceMinor());
    }

    @Test
    void reservesOneWheatSeedForReplantingAndStoresOnlyTheSurplus() {
        assertEquals(0, FarmerRuntime.wheatSeedSurplus(0));
        assertEquals(0, FarmerRuntime.wheatSeedSurplus(1));
        assertEquals(2, FarmerRuntime.wheatSeedSurplus(3));
    }

    @Test
    void visitorPurchaseMovesStockValueIntoVillageBalanceOnce() throws IOException {
        NpcEconomy economy = economy(64, 32);
        UUID farmer = UUID.randomUUID();
        economy.addProduction(farmer, "village", "wheat", 5, 1);

        VisitorPurchaseResult first = economy.visitorPurchase("village", "visitor:test", 600L, 3);
        VisitorPurchaseResult duplicate = economy.visitorPurchase("village", "visitor:test", 600L, 3);

        assertEquals(2, first.purchased().get("wheat"));
        assertEquals(500L, first.spentMinor());
        assertEquals(3, economy.villageAccount("village").quantity("wheat"));
        assertEquals(500L, economy.villageAccount("village").balanceMinor());
        assertTrue(duplicate.purchased().isEmpty());
    }

    @Test
    void visitorPurchaseLimitsTotalItemsAcrossDifferentProducts() throws IOException {
        NpcEconomy economy = economy(64, 32);
        assertTrue(economy.addVillageLoot(
                "village", java.util.Map.of("wheat", 3, "carrot", 3, "potato", 3)));

        VisitorPurchaseResult result = economy.visitorPurchase("village", "visitor:bounded", 10_000L, 3);

        assertEquals(3, result.purchased().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(6, economy.villageAccount("village").inventorySize());
    }

    @Test
    void byproductsUseStorageButDoNotConsumeShiftQuota() throws IOException {
        NpcEconomy economy = economy(64, 2);
        UUID npc = UUID.randomUUID();

        assertTrue(economy.addProduction(npc, "village", "wheat", 1, 1));
        assertTrue(economy.addByproduct(npc, "village", "wheat_seeds", 3, 1));

        assertEquals(1, economy.account(npc).producedThisShift());
        assertEquals(3, economy.villageAccount("village").quantity("wheat_seeds"));
        assertTrue(economy.canAcceptHarvest(npc, "village", 1, 3, 1));
    }

    @Test
    void rancherConsumesVillageFoodWithoutTouchingProductionQuota() throws IOException {
        NpcEconomy economy = economy(64, 32);
        UUID farmer = UUID.randomUUID();
        economy.addProduction(farmer, "village", "wheat", 4, 1);

        assertTrue(economy.consumeVillageItem("village", "wheat", 2));
        assertFalse(economy.consumeVillageItem("village", "wheat", 3));

        assertEquals(2, economy.villageAccount("village").quantity("wheat"));
        assertEquals(4, economy.account(farmer).producedThisShift());
    }

    @Test
    void ranchLootEntersVillageStorage() throws IOException {
        NpcEconomy economy = economy(64, 32);

        assertTrue(economy.addVillageLoot("village", java.util.Map.of("beef", 3, "leather", 1)));

        assertEquals(3, economy.villageAccount("village").quantity("beef"));
        assertEquals(1, economy.villageAccount("village").quantity("leather"));
    }

    @Test
    void carriedLootUsesTwoMinecraftStacksAndDepositsAtomically() throws IOException {
        NpcEconomy economy = economy(512, 32);
        UUID rancher = UUID.randomUUID();

        assertTrue(economy.addCarriedLoot(rancher, java.util.Map.of("egg", 64)));
        assertTrue(economy.addCarriedLoot(rancher, java.util.Map.of("feather", 2)));
        assertTrue(economy.carriedInventoryFull(rancher));
        assertFalse(economy.addCarriedLoot(rancher, java.util.Map.of("chicken", 1)));

        assertTrue(economy.depositCarriedLoot(rancher, "village"));
        assertEquals(0, economy.account(rancher).inventorySize());
        assertEquals(64, economy.villageAccount("village").quantity("egg"));
        assertEquals(2, economy.villageAccount("village").quantity("feather"));
    }

    @Test
    void fisherHasASeparatePersistableRoleQuotaWithinTheGlobalQuota() throws IOException {
        NpcEconomy economy = economy(64, 32);
        UUID npc = UUID.randomUUID();

        assertTrue(economy.addRoleProduction(
                npc, "village", ResidentRole.FISHER, "cod", 1, 2, 10));
        assertTrue(economy.addRoleProduction(
                npc, "village", ResidentRole.FISHER, "salmon", 1, 2, 10));
        assertFalse(economy.canAcceptRoleProduction(
                npc, "village", ResidentRole.FISHER, 1, 2, 10));

        assertEquals(2, economy.account(npc).roleProduction("fisher"));
        assertEquals(2, economy.account(npc).producedThisShift());
        economy.flush();
        NpcEconomy reloaded = new NpcEconomy(
                new NpcEconomyStore(temporaryDirectory.toFile(), Logger.getAnonymousLogger()),
                new NpcPriceBook(temporaryDirectory.toFile()),
                LivingNpcConfig.load(new YamlConfiguration()));
        assertEquals(2, reloaded.account(npc).roleProduction("fisher"));
    }

    @Test
    void transformsVillageItemsAtomicallyWithinRoleQuota() throws IOException {
        NpcEconomy economy = economy(5, 4);
        UUID npc = UUID.randomUUID();
        assertTrue(economy.addVillageLoot("village", java.util.Map.of("wheat", 4)));

        assertTrue(economy.transformVillageItems(
                npc, "village", ResidentRole.CRAFTER,
                java.util.Map.of("wheat", 2), "bread", 1, 2, 1));
        assertFalse(economy.transformVillageItems(
                npc, "village", ResidentRole.CRAFTER,
                java.util.Map.of("wheat", 3), "bread", 1, 2, 1));

        assertEquals(2, economy.villageAccount("village").quantity("wheat"));
        assertEquals(1, economy.villageAccount("village").quantity("bread"));
        assertEquals(1, economy.account(npc).roleProduction("crafter"));
    }

    private NpcEconomy economy(int capacity, int quota) throws IOException {
        return economy(capacity, quota, false);
    }

    private NpcEconomy economy(int capacity, int quota, boolean unlimitedStorage) throws IOException {
        Files.writeString(temporaryDirectory.resolve("prices.yml"), "npc-prices:\n  wheat: 2.5\n");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("economy.inventory-capacity", capacity);
        yaml.set("economy.unlimited-storage", unlimitedStorage);
        yaml.set("economy.output-per-action", 1);
        yaml.set("economy.max-output-per-shift", quota);
        LivingNpcConfig config = LivingNpcConfig.load(yaml);
        return new NpcEconomy(
                new NpcEconomyStore(temporaryDirectory.toFile(), Logger.getAnonymousLogger()),
                new NpcPriceBook(temporaryDirectory.toFile()),
                config);
    }
}
