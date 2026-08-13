package vn.heomc.livingnpc;

import java.util.Map;

record VisitorSettings(
        boolean enabled,
        int maxActive,
        long spawnIntervalMinTicks,
        long spawnIntervalMaxTicks,
        long walletMinMinor,
        long walletMaxMinor,
        int maxPurchaseItems,
        Map<String, Integer> stockReserves,
        long shoppingDurationTicks,
        long lifetimeTicks,
        double activationRange) {
    VisitorSettings {
        stockReserves = Map.copyOf(stockReserves);
    }
}
