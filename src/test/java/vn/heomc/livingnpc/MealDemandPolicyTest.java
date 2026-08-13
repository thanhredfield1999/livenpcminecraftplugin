package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MealDemandPolicyTest {
    @Test
    void batchesResidentDemandWithBufferInsteadOfOneRequestPerNpc() {
        MealDemandSnapshot snapshot = MealDemandPolicy.snapshot(
                new MealOpportunity(MealPeriod.LUNCH, 4L), 5, 0, 1, settings(2, 8, 0));

        assertEquals("4:lunch", snapshot.opportunityId());
        assertEquals(5, snapshot.residentDemand());
        assertEquals(6, snapshot.requestedServings());
    }

    @Test
    void capsBatchAndVisitorDemandIndependently() {
        MealDemandSnapshot snapshot = MealDemandPolicy.snapshot(
                new MealOpportunity(MealPeriod.DINNER, 2L), 10, 6, 0, settings(2, 8, 2));

        assertEquals(10, snapshot.residentDemand());
        assertEquals(2, snapshot.visitorDemand());
        assertEquals(8, snapshot.requestedServings());
    }

    @Test
    void existingServingStockReducesOnlyTheNewBatch() {
        MealDemandSnapshot snapshot = MealDemandPolicy.snapshot(
                new MealOpportunity(MealPeriod.BREAKFAST, 1L), 3, 0, 4, settings(2, 8, 0));

        assertEquals(1, snapshot.requestedServings());
        assertEquals(4, snapshot.availableServings());
    }

    private SeasonTenSettings settings(int buffer, int maxBatch, int visitorQuota) {
        return new SeasonTenSettings(
                true, 500L, 2500L, 5500L, 7500L, 10_500L, 12_500L,
                buffer, maxBatch, visitorQuota, true);
    }
}
