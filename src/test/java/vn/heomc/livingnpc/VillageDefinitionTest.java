package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

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
}
