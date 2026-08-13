package vn.heomc.livingnpc;

final class MealDemandPolicy {
    private MealDemandPolicy() {
    }

    static MealDemandSnapshot snapshot(
            MealOpportunity opportunity,
            int waitingResidents,
            int waitingVisitors,
            int availableServings,
            SeasonTenSettings settings) {
        if (opportunity == null || !settings.enabled()) {
            return new MealDemandSnapshot("", 0, 0, Math.max(0, availableServings), 0);
        }
        int residents = Math.max(0, waitingResidents);
        int visitors = Math.min(Math.max(0, waitingVisitors), settings.visitorQuotaPerBatch());
        int available = Math.max(0, availableServings);
        long demandWithBuffer = (long) residents + visitors + settings.demandBuffer();
        int target = (int) Math.min(settings.maxBatchSize(), demandWithBuffer);
        return new MealDemandSnapshot(
                opportunity.id(), residents, visitors, available, Math.max(0, target - available));
    }
}
