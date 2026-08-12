package vn.heomc.livingnpc;

record VisitorSettings(
        boolean enabled,
        int maxActive,
        long spawnIntervalMinTicks,
        long spawnIntervalMaxTicks,
        long walletMinMinor,
        long walletMaxMinor,
        int maxPurchaseItems,
        long shoppingDurationTicks,
        long lifetimeTicks,
        double activationRange) {
}
