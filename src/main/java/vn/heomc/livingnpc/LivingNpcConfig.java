package vn.heomc.livingnpc;

import org.bukkit.configuration.file.FileConfiguration;

record LivingNpcConfig(
        long tickInterval,
        double activationRange,
        long workStartTick,
        long workEndTick,
        long workScanIntervalTicks,
        int actionDelayMinTicks,
        int actionDelayMaxTicks,
        int inspectionDurationTicks,
        int ambientIntervalMinTicks,
        int ambientIntervalMaxTicks,
        int ambientDurationMinTicks,
        int ambientDurationMaxTicks,
        double playerNoticeRange,
        double dangerRange,
        int wanderRadius,
        int inventoryCapacity,
        int outputPerAction,
        int maxOutputPerShift,
        boolean sellAtShiftEnd,
        String currencyName,
        long navigationTimeoutTicks,
        long navigationRetryBackoffTicks,
        int maxPlotRadius,
        float navigationSpeedModifier,
        double navigationDistanceMargin) {

    static LivingNpcConfig load(FileConfiguration config) {
        int minDelay = Math.max(1, config.getInt("action-delay-min-ticks", 20));
        int maxDelay = Math.max(minDelay, config.getInt("action-delay-max-ticks", 60));
        int ambientIntervalMin = Math.max(20, config.getInt("ambient.interval-min-ticks", 100));
        int ambientIntervalMax = Math.max(ambientIntervalMin, config.getInt("ambient.interval-max-ticks", 240));
        int ambientDurationMin = Math.max(10, config.getInt("ambient.duration-min-ticks", 30));
        int ambientDurationMax = Math.max(ambientDurationMin, config.getInt("ambient.duration-max-ticks", 80));
        return new LivingNpcConfig(
                Math.max(1L, config.getLong("tick-interval", 10L)),
                Math.max(1.0, config.getDouble("activation-range", 48.0)),
                config.getLong("work-start-tick", 1000L),
                config.getLong("work-end-tick", 12000L),
                Math.max(20L, config.getLong("work-scan-interval-ticks", 100L)),
                minDelay,
                maxDelay,
                Math.max(5, config.getInt("inspection-duration-ticks", 12)),
                ambientIntervalMin,
                ambientIntervalMax,
                ambientDurationMin,
                ambientDurationMax,
                Math.max(1.0, config.getDouble("ambient.player-notice-range", 7.0)),
                Math.max(1.0, config.getDouble("danger-range", 10.0)),
                Math.max(1, config.getInt("ambient.wander-radius", 3)),
                Math.max(1, config.getInt("economy.inventory-capacity", 512)),
                Math.max(1, config.getInt("economy.output-per-action", 1)),
                Math.max(1, config.getInt("economy.max-output-per-shift", 32)),
                config.getBoolean("economy.sell-at-shift-end", true),
                config.getString("economy.currency-name", "Xu đồng"),
                Math.max(100L, config.getLong("navigation-timeout-ticks", 400L)),
                Math.max(20L, config.getLong("navigation.retry-backoff-ticks", 60L)),
                Math.max(1, config.getInt("max-plot-radius", 8)),
                (float) Math.max(0.1, config.getDouble("navigation.speed-modifier", 0.85)),
                Math.max(0.5, config.getDouble("navigation.distance-margin", 1.5)));
    }
}
