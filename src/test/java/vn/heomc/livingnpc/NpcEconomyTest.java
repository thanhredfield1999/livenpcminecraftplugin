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
        NpcEconomy economy = economy(4, 3);
        UUID npc = UUID.randomUUID();

        assertTrue(economy.addProduction(npc, "wheat", 2, 1));
        assertTrue(economy.addProduction(npc, "wheat", 2, 1));
        assertFalse(economy.addProduction(npc, "wheat", 1, 1));
        assertEquals(3, economy.townAccount().inventorySize());
        assertEquals(3, economy.account(npc).producedThisShift());
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

    private NpcEconomy economy(int capacity, int quota) throws IOException {
        Files.writeString(temporaryDirectory.resolve("prices.yml"), "npc-prices:\n  wheat: 2.5\n");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("economy.inventory-capacity", capacity);
        yaml.set("economy.output-per-action", 1);
        yaml.set("economy.max-output-per-shift", quota);
        LivingNpcConfig config = LivingNpcConfig.load(yaml);
        return new NpcEconomy(
                new NpcEconomyStore(temporaryDirectory.toFile(), Logger.getAnonymousLogger()),
                new NpcPriceBook(temporaryDirectory.toFile()),
                config);
    }
}
