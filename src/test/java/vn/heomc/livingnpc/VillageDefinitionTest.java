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
    void storesAtMostNineDistinctRanchPens() {
        StoredLocation center = new StoredLocation("StillCliff", 0, 64, 0, 0, 0);
        VillageDefinition village = new VillageDefinition("village", "Village", center, null, null, null);

        VillageDefinition changed = village;
        for (int index = 1; index <= 10; index++) {
            changed = changed.withRanchPen(new RanchPen(
                    "ranch_" + index, new StoredLocation("StillCliff", index * 20, 64, 0, 0, 0)));
        }
        VillageDefinition duplicate = changed.withRanchPen(new RanchPen(
                "duplicate", new StoredLocation("StillCliff", 20.8, 64.5, 0.2, 90, 0)));

        assertEquals(9, changed.ranchPens().size());
        assertEquals(changed, duplicate);
        assertEquals(8, changed.withoutRanchPen("ranch_1").ranchPens().size());
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

    @Test
    void miningZonesUseTwoByTwoFootprintsAndRejectOverlap() {
        StoredLocation center = new StoredLocation("StillCliff", 0, 64, 0, 0, 0);
        VillageDefinition village = new VillageDefinition("village", "Village", center, null, null, null);
        MiningZone first = new MiningZone(
                "mine_1", new StoredLocation("StillCliff", 10, 50, 10, 0, 0), 48, 52);
        MiningZone overlap = new MiningZone(
                "mine_2", new StoredLocation("StillCliff", 11, 52, 11, 0, 0), 50, 54);
        MiningZone separate = new MiningZone(
                "mine_2", new StoredLocation("StillCliff", 20, 50, 20, 0, 0), 48, 52);

        VillageDefinition changed = village.withMiningZone(first);

        assertTrue(first.contains("StillCliff", 10, 48, 10));
        assertTrue(first.contains("StillCliff", 11, 52, 11));
        assertTrue(!first.contains("StillCliff", 12, 50, 11));
        assertEquals(changed, changed.withMiningZone(overlap));
        assertEquals(2, changed.withMiningZone(separate).miningZones().size());
    }

    @Test
    void storesActivityPointsByStableId() {
        StoredLocation center = new StoredLocation("StillCliff", 0, 64, 0, 0, 0);
        VillageDefinition village = new VillageDefinition("village", "Village", center, null, null, null);
        ActivityPoint first = new ActivityPoint(
                "home_exit_1", ActivityPointType.HOME_EXIT, center,
                new StoredLocation("StillCliff", 3, 64, 0, 90, 0), 1,
                null, null, java.util.Set.of(), null);
        ActivityPoint replacement = new ActivityPoint(
                "home_exit_1", ActivityPointType.HOME_EXIT, center,
                new StoredLocation("StillCliff", 4, 64, 0, 90, 0), 1,
                null, null, java.util.Set.of(), null);

        VillageDefinition changed = village.withActivityPoint(first).withActivityPoint(replacement);

        assertTrue(village.activityPoints().isEmpty());
        assertEquals(1, changed.activityPoints().size());
        assertEquals(replacement, changed.activityPoints().getFirst());
        assertTrue(changed.withoutActivityPoint("home_exit_1").activityPoints().isEmpty());
    }
}
