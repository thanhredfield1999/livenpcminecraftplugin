package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import java.util.UUID;

class VillageDefinitionTest {
    @Test
    void updatesOnlyTheSelectedVillagesSocialPoint() {
        StoredLocation northCenter = new StoredLocation("StillCliff", 0, 64, 0, 0, 0);
        StoredLocation southCenter = new StoredLocation("StillCliff", 500, 64, 500, 0, 0);
        StoredLocation market = new StoredLocation("StillCliff", 10, 64, 10, 0, 0);
        VillageDefinition north = new VillageDefinition("north", "Làng Bắc", northCenter, null, null, null);
        VillageDefinition south = new VillageDefinition("south", "Làng Nam", southCenter, null, null, null);

        VillageDefinition changedNorth = north.withSocialPoint("cho", market);

        assertEquals(market, changedNorth.marketPoint());
        assertEquals(northCenter, changedNorth.center());
        assertEquals(southCenter, south.center());
        assertNull(south.marketPoint());
    }

    @Test
    void updatesOnlyOneWorkZoneImmutably() {
        StoredLocation center = new StoredLocation("StillCliff", 0, 64, 0, 0, 0);
        StoredLocation wood = new StoredLocation("StillCliff", 10, 64, 10, 0, 0);
        VillageDefinition village = new VillageDefinition("village", "Village", center, null, null, null);

        VillageDefinition changed = village.withWorkZone(VillageWorkZoneType.WOOD, wood);

        assertNull(village.workZone(VillageWorkZoneType.WOOD));
        assertEquals(wood, changed.workZone(VillageWorkZoneType.WOOD));
        assertNull(changed.workZone(VillageWorkZoneType.COOKING));
    }

    @Test
    void storesVisitorGateSeparatelyFromVillageCenter() {
        StoredLocation center = new StoredLocation("StillCliff", 0, 64, 0, 0, 0);
        StoredLocation gate = new StoredLocation("StillCliff", 30, 64, 30, 0, 0);
        VillageDefinition village = new VillageDefinition("village", "Village", center, null, null, null);

        VillageDefinition changed = village.withVisitorGate(gate);

        assertNull(village.visitorGate());
        assertEquals(gate, changed.visitorGate());
        assertEquals(center, changed.center());
    }

    @Test
    void storesMultipleDeliveryLocationsWithoutDuplicates() {
        StoredLocation center = new StoredLocation("StillCliff", 0, 64, 0, 0, 0);
        StoredLocation first = new StoredLocation("StillCliff", 10, 64, 10, 0, 0);
        StoredLocation duplicateBlock = new StoredLocation("StillCliff", 10.9, 64.2, 10.1, 90, 0);
        StoredLocation second = new StoredLocation("StillCliff", 20, 64, 20, 0, 0);
        VillageDefinition village = new VillageDefinition("village", "Village", center, null, null, null);

        VillageDefinition changed = village.withDeliveryChest(first)
                .withDeliveryChest(duplicateBlock).withDeliveryChest(second);

        assertTrue(village.deliveryLocations().isEmpty());
        assertEquals(2, changed.deliveryLocations().size());
        assertEquals(second, changed.deliveryLocations().get(1));
        assertEquals(1, changed.withoutDeliveryLocation(0).deliveryLocations().size());
    }

    @Test
    void ranchAnimalLimitIsBounded() {
        StoredLocation center = new StoredLocation("StillCliff", 0, 64, 0, 0, 0);
        VillageDefinition village = new VillageDefinition("village", "Village", center, null, null, null);

        assertEquals(8, village.ranchAnimalLimit());
        assertEquals(2, village.withRanchAnimalLimit(1).ranchAnimalLimit());
        assertEquals(64, village.withRanchAnimalLimit(100).ranchAnimalLimit());
    }

    @Test
    void storesFishingZoneSeparatelyFromOtherWorkZones() {
        StoredLocation center = new StoredLocation("StillCliff", 0, 64, 0, 0, 0);
        StoredLocation fishing = new StoredLocation("StillCliff", 12, 63, 8, 0, 0);
        VillageDefinition village = new VillageDefinition("village", "Village", center, null, null, null);

        VillageDefinition changed = village.withWorkZone(VillageWorkZoneType.FISHING, fishing);

        assertEquals(fishing, changed.workZone(VillageWorkZoneType.FISHING));
        assertNull(changed.workZone(VillageWorkZoneType.RANCH));
    }

    @Test
    void storesSeatsWithoutDuplicatingTheSameBlock() {
        StoredLocation center = new StoredLocation("StillCliff", 0, 64, 0, 0, 0);
        VillageDefinition village = new VillageDefinition("village", "Village", center, null, null, null);
        SeatDefinition rest = new SeatDefinition(
                "rest", new StoredLocation("StillCliff", 10.5, 64.5, 10.5, 90, 0), SeatType.REST);
        SeatDefinition dining = new SeatDefinition(
                "dining", new StoredLocation("StillCliff", 10.7, 64.9, 10.2, 90, 0), SeatType.DINING);

        VillageDefinition changed = village.withSeat(rest).withSeat(dining);

        assertTrue(village.seats().isEmpty());
        assertEquals(1, changed.seats().size());
        assertEquals("rest", changed.seats().getFirst().id());
        assertEquals(dining.location(), changed.seats().getFirst().location());
        assertEquals(SeatType.DINING, changed.seats().getFirst().type());
        assertTrue(changed.withoutSeat("rest").seats().isEmpty());
    }

    @Test
    void storesIndependentMerchantStallsByNpc() {
        StoredLocation center = new StoredLocation("StillCliff", 0, 64, 0, 0, 0);
        UUID firstMerchant = UUID.randomUUID();
        UUID secondMerchant = UUID.randomUUID();
        MerchantStall first = new MerchantStall(
                firstMerchant,
                new StoredLocation("StillCliff", 10.5, 64, 10.5, 90, 0),
                new StoredLocation("StillCliff", 11.5, 64, 10.5, -90, 0));
        MerchantStall second = new MerchantStall(
                secondMerchant,
                new StoredLocation("StillCliff", 20.5, 64, 20.5, 0, 0),
                new StoredLocation("StillCliff", 20.5, 64, 21.5, 180, 0));
        VillageDefinition village = new VillageDefinition("village", "Village", center, null, null, null);

        VillageDefinition changed = village.withMerchantStall(first).withMerchantStall(second);

        assertTrue(village.merchantStalls().isEmpty());
        assertEquals(first, changed.merchantStall(firstMerchant));
        assertEquals(second, changed.merchantStall(secondMerchant));
        assertEquals(1, changed.withoutMerchantStall(firstMerchant).merchantStalls().size());
    }

    @Test
    void merchantStallRequiresBothPointsInTheSameWorld() {
        UUID merchant = UUID.randomUUID();
        StoredLocation seller = new StoredLocation("StillCliff", 10, 64, 10, 0, 0);
        StoredLocation buyer = new StoredLocation("OtherWorld", 11, 64, 10, 180, 0);

        assertTrue(!new MerchantStall(merchant, seller, null).complete());
        assertTrue(!new MerchantStall(merchant, seller, buyer).complete());
        assertTrue(new MerchantStall(merchant, seller,
                new StoredLocation("StillCliff", 11, 64, 10, 180, 0)).complete());
    }
}
