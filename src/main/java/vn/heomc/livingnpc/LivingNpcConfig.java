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
        boolean unlimitedStorage,
        int outputPerAction,
        int maxOutputPerShift,
        boolean sellAtShiftEnd,
        String currencyName,
        long navigationTimeoutTicks,
        long navigationRetryBackoffTicks,
        int maxPlotRadius,
        float navigationSpeedModifier,
        double navigationDistanceMargin,
        int workZoneValidationRadius,
        int workZoneValidationVerticalRange,
        FarmerDailyPlanSettings farmerDailyPlan,
        VisitorSettings visitors,
        RancherSettings rancher,
        FisherSettings fisher,
        MinerSettings miner,
        ResidentPatrolSettings residentPatrol,
        SeatingSettings seating) {

    static LivingNpcConfig load(FileConfiguration config) {
        int minDelay = Math.max(1, config.getInt("action-delay-min-ticks", 20));
        int maxDelay = Math.max(minDelay, config.getInt("action-delay-max-ticks", 60));
        int ambientIntervalMin = Math.max(20, config.getInt("ambient.interval-min-ticks", 100));
        int ambientIntervalMax = Math.max(ambientIntervalMin, config.getInt("ambient.interval-max-ticks", 240));
        int ambientDurationMin = Math.max(10, config.getInt("ambient.duration-min-ticks", 30));
        int ambientDurationMax = Math.max(ambientDurationMin, config.getInt("ambient.duration-max-ticks", 80));
        long fisherDelayMin = Math.max(200L, config.getLong("fisher.attempt-delay-min-ticks", 500L));
        long fisherDelayMax = Math.max(fisherDelayMin, config.getLong("fisher.attempt-delay-max-ticks", 900L));
        long patrolCooldownMin = Math.max(40L, config.getLong("resident.patrol.trip-cooldown-min-ticks", 160L));
        long patrolCooldownMax = Math.max(
                patrolCooldownMin, config.getLong("resident.patrol.trip-cooldown-max-ticks", 360L));
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
                config.getBoolean("economy.unlimited-storage", true),
                Math.max(1, config.getInt("economy.output-per-action", 1)),
                Math.max(1, config.getInt("economy.max-output-per-shift", 32)),
                config.getBoolean("economy.sell-at-shift-end", true),
                config.getString("economy.currency-name", "Xu đồng"),
                Math.max(100L, config.getLong("navigation-timeout-ticks", 400L)),
                Math.max(20L, config.getLong("navigation.retry-backoff-ticks", 60L)),
                Math.max(1, config.getInt("max-plot-radius", 8)),
                (float) Math.max(0.1, config.getDouble("navigation.speed-modifier", 0.85)),
                Math.max(0.5, config.getDouble("navigation.distance-margin", 1.5)),
                Math.clamp(config.getInt("work-zones.validation-radius", 6), 1, 16),
                Math.clamp(config.getInt("work-zones.validation-vertical-range", 3), 0, 8),
                new FarmerDailyPlanSettings(
                        config.getBoolean("farmer.daily-plan.enabled", true),
                        Math.clamp(config.getLong("farmer.daily-plan.lunch-duration-ticks", 1000L), 0L, 6000L)),
                loadVisitors(config),
                new RancherSettings(
                        Math.max(100L, config.getLong("rancher.scan-interval-ticks", 200L)),
                        Math.max(100L, config.getLong("rancher.action-cooldown-ticks", 600L)),
                        Math.clamp(config.getInt("rancher.love-mode-ticks", 600), 100, 1200),
                        Math.clamp(config.getInt("rancher.max-cull-per-cycle", 2), 1, 4),
                        Math.max(2.0, config.getDouble("rancher.interaction-range", 4.0)),
                        Math.clamp(config.getInt("rancher.escape-search-radius", 12), 7, 24),
                        Math.max(100L, config.getLong("rancher.patrol-interval-ticks", 240L))),
                new FisherSettings(
                        fisherDelayMin,
                        fisherDelayMax,
                        Math.clamp(config.getDouble("fisher.success-chance", 0.70), 0.0, 1.0),
                        Math.clamp(config.getInt("fisher.max-catch-per-shift", 12), 1, 64),
                        Math.clamp(config.getInt("fisher.water-search-radius", 4), 1, 12),
                        Math.clamp(config.getInt("fisher.water-search-vertical-range", 2), 0, 6)),
                new MinerSettings(
                        Math.max(100L, config.getLong("miner.scan-interval-ticks", 200L)),
                        Math.max(120L, config.getLong("miner.break-delay-ticks", 120L)),
                        Math.clamp(config.getLong("miner.swing-interval-ticks", 10L), 5L, 40L),
                        Math.clamp(config.getInt("miner.search-radius", 6), 1, 12),
                        Math.clamp(config.getInt("miner.vertical-range", 3), 0, 8),
                        Math.clamp(config.getInt("miner.avoidance-radius", 2), 1, 4),
                        Math.clamp(config.getInt("miner.minimum-travel-distance", 3), 0, 8)),
                new ResidentPatrolSettings(
                        config.getBoolean("resident.patrol.enabled", true),
                        Math.max(200L, config.getLong("resident.patrol.scan-interval-ticks", 600L)),
                        Math.clamp(config.getInt("resident.patrol.scan-radius", 32), 4, 64),
                        Math.clamp(config.getInt("resident.patrol.vertical-range", 4), 1, 12),
                        Math.clamp(config.getInt("resident.patrol.max-cached-paths", 512), 32, 2048),
                        Math.clamp(config.getInt("resident.patrol.scan-blocks-per-tick", 256), 32, 2048),
                        Math.clamp(config.getInt("resident.patrol.min-target-distance", 8), 2, 32),
                        Math.clamp(config.getInt("resident.patrol.max-target-distance", 24), 4, 64),
                        patrolCooldownMin,
                        patrolCooldownMax),
                loadSeating(config));
    }

    private static VisitorSettings loadVisitors(FileConfiguration config) {
        long minInterval = Math.max(100L, config.getLong("visitors.spawn-interval-min-ticks", 1200L));
        long maxInterval = Math.max(minInterval, config.getLong("visitors.spawn-interval-max-ticks", 2400L));
        long walletMin = Math.max(0L, config.getLong("visitors.wallet-min-minor", 300L));
        return new VisitorSettings(
                config.getBoolean("visitors.enabled", false),
                Math.clamp(config.getInt("visitors.max-active", 3), 0, 16),
                minInterval,
                maxInterval,
                walletMin,
                Math.max(walletMin, config.getLong("visitors.wallet-max-minor", 1500L)),
                Math.clamp(config.getInt("visitors.max-purchase-items", 3), 1, 8),
                Math.max(40L, config.getLong("visitors.shopping-duration-ticks", 120L)),
                Math.max(600L, config.getLong("visitors.lifetime-ticks", 2400L)),
                Math.max(16.0, config.getDouble("visitors.activation-range", 64.0)));
    }

    private static SeatingSettings loadSeating(FileConfiguration config) {
        long minimum = Math.max(40L, config.getLong("seating.rest-duration-min-ticks", 100L));
        return new SeatingSettings(
                config.getBoolean("seating.enabled", true),
                minimum,
                Math.max(minimum, config.getLong("seating.rest-duration-max-ticks", 240L)),
                Math.clamp(config.getLong("seating.stand-duration-ticks", 8L), 1L, 40L));
    }
}
