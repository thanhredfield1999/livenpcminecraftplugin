package vn.heomc.livingnpc;

record MealDemandSnapshot(
        String opportunityId,
        int residentDemand,
        int visitorDemand,
        int availableServings,
        int requestedServings) {
}
