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
          SeasonFiveSettings seasonFive,
          SeasonSixSettings seasonSix,
         SeasonTenSettings seasonTen,
         SeasonElevenSettings seasonEleven,
         RancherSettings rancher,
        FisherSettings fisher,
        MinerSettings miner,
        ResidentPatrolSettings residentPatrol,
        SeatingSettings seating,
        NeedsSettings needs,
        SeasonEightSettings seasonEight) {

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
                Math.max(10L, config.getLong("tick-interval", 10L)),
                Math.max(1.0, config.getDouble("activation-range", 48.0)),
                config.getLong("work-start-tick", 1000L),
                config.getLong("work-end-tick", 12000L),
                Math.max(100L, config.getLong("work-scan-interval-ticks", 100L)),
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
                Math.clamp(config.getInt("max-plot-radius", 8), 1, 8),
                (float) Math.max(0.1, config.getDouble("navigation.speed-modifier", 0.85)),
                Math.max(0.5, config.getDouble("navigation.distance-margin", 1.5)),
                Math.clamp(config.getInt("work-zones.validation-radius", 6), 1, 16),
                Math.clamp(config.getInt("work-zones.validation-vertical-range", 3), 0, 8),
                new FarmerDailyPlanSettings(
                        config.getBoolean("farmer.daily-plan.enabled", true),
                        Math.clamp(config.getLong("farmer.daily-plan.lunch-duration-ticks", 1000L), 0L, 6000L)),
                 loadVisitors(config),
                 loadSeasonFive(config),
                 new SeasonSixSettings(
                         config.getBoolean("season-6.enabled", false),
                         config.getLong("season-6.morning-exit-timeout-ticks", 600L)),
                  loadSeasonTen(config),
                  loadSeasonEleven(config),
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
                        Math.max(60L, config.getLong("miner.restoration-delay-seconds", 1800L)),
                        Math.clamp(config.getInt("miner.batch-size", 4), 1, 4)),
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
                loadSeating(config),
                loadNeeds(config),
                new SeasonEightSettings(
                        config.getBoolean("season-8.enabled", false),
                        Math.clamp(config.getInt("season-8.max-batch", 4), 1, 16)));
    }

    private static VisitorSettings loadVisitors(FileConfiguration config) {
        long minInterval = Math.max(100L, config.getLong("visitors.spawn-interval-min-ticks", 1200L));
        long maxInterval = Math.max(minInterval, config.getLong("visitors.spawn-interval-max-ticks", 2400L));
        long walletMin = Math.max(0L, config.getLong("visitors.wallet-min-minor", 300L));
        java.util.Map<String, Integer> reserves = new java.util.LinkedHashMap<>();
        reserves.put("wheat", Math.max(0, config.getInt("visitors.stock-reserves.wheat", 8)));
        reserves.put("wheat_seeds", Math.max(0, config.getInt("visitors.stock-reserves.wheat_seeds", 8)));
        reserves.put("carrot", Math.max(0, config.getInt("visitors.stock-reserves.carrot", 8)));
        org.bukkit.configuration.ConfigurationSection reserveSection =
                config.getConfigurationSection("visitors.stock-reserves");
        if (reserveSection != null) {
            for (String itemKey : reserveSection.getKeys(false)) {
                if (!itemKey.isBlank()) reserves.put(itemKey, Math.max(0, reserveSection.getInt(itemKey)));
            }
        }
        return new VisitorSettings(
                config.getBoolean("visitors.enabled", false),
                Math.clamp(config.getInt("visitors.max-active", 3), 0, 16),
                minInterval,
                maxInterval,
                walletMin,
                Math.max(walletMin, config.getLong("visitors.wallet-max-minor", 1500L)),
                Math.clamp(config.getInt("visitors.max-purchase-items", 3), 1, 8),
                reserves,
                Math.max(40L, config.getLong("visitors.shopping-duration-ticks", 120L)),
                Math.max(600L, config.getLong("visitors.lifetime-ticks", 2400L)),
                Math.max(16.0, config.getDouble("visitors.activation-range", 64.0)));
    }

     private static SeasonFiveSettings loadSeasonFive(FileConfiguration config) {
        int followerMin = Math.clamp(config.getInt("season-5.caravan.follower-min", 1), 0, 2);
        int followerMax = Math.clamp(config.getInt("season-5.caravan.follower-max", 2), followerMin, 2);
        return new SeasonFiveSettings(
                config.getBoolean("season-5.enabled", false),
                Math.clamp(config.getInt("season-5.market-day.interval-days", 7), 1, 30),
                Math.max(0, config.getInt("season-5.market-day.day-offset", 0)),
                Math.floorMod(config.getLong("season-5.market-day.start-tick", 1000L), 24_000L),
                Math.floorMod(config.getLong("season-5.market-day.end-tick", 12_000L), 24_000L),
                followerMin,
                followerMax,
                Math.clamp(config.getDouble("season-5.caravan.pack-animal-chance", 0.5), 0.0, 1.0),
                Math.clamp(config.getDouble("season-5.caravan.formation-spacing", 2.0), 1.5, 4.0));
     }

      private static SeasonTenSettings loadSeasonTen(FileConfiguration config) {
         return new SeasonTenSettings(
                 config.getBoolean("season-10.enabled", false),
                 Math.floorMod(config.getLong("season-10.meals.breakfast.start-tick", 500L), 24_000L),
                 Math.floorMod(config.getLong("season-10.meals.breakfast.end-tick", 2500L), 24_000L),
                 Math.floorMod(config.getLong("season-10.meals.lunch.start-tick", 5500L), 24_000L),
                 Math.floorMod(config.getLong("season-10.meals.lunch.end-tick", 7500L), 24_000L),
                 Math.floorMod(config.getLong("season-10.meals.dinner.start-tick", 10500L), 24_000L),
                 Math.floorMod(config.getLong("season-10.meals.dinner.end-tick", 12500L), 24_000L),
                 Math.clamp(config.getInt("season-10.serving.demand-buffer", 2), 0, 16),
                 Math.clamp(config.getInt("season-10.serving.max-batch-size", 8), 1, 64),
                 Math.clamp(config.getInt("season-10.serving.visitor-quota-per-batch", 0), 0, 16),
                 config.getBoolean("season-10.fallback-stored-food", true));
      }

      private static SeasonElevenSettings loadSeasonEleven(FileConfiguration config) {
          java.util.Map<EconomicSeason, SeasonalEconomyModifiers> modifiers =
                  new java.util.EnumMap<>(EconomicSeason.class);
          for (EconomicSeason season : EconomicSeason.values()) {
              String path = "season-11.modifiers." + season.name().toLowerCase();
              modifiers.put(season, new SeasonalEconomyModifiers(
                      Math.clamp(config.getInt(path + ".stock-target-percent", 100), 50, 200),
                      Math.clamp(config.getInt(path + ".export-demand-percent", 100), 50, 200),
                      Math.clamp(config.getInt(path + ".labor-priority-percent", 100), 50, 200)));
          }
          return new SeasonElevenSettings(
                  config.getBoolean("season-11.enabled", false),
                  Math.clamp(config.getInt("season-11.days-per-season", 7), 1, 30),
                  Math.max(0L, config.getLong("season-11.start-day", 0L)),
                  modifiers);
      }

    private static SeatingSettings loadSeating(FileConfiguration config) {
        long minimum = Math.max(40L, config.getLong("seating.rest-duration-min-ticks", 100L));
        return new SeatingSettings(
                config.getBoolean("seating.enabled", true),
                minimum,
                Math.max(minimum, config.getLong("seating.rest-duration-max-ticks", 240L)),
                Math.clamp(config.getLong("seating.stand-duration-ticks", 8L), 1L, 40L));
    }

    private static NeedsSettings loadNeeds(FileConfiguration config) {
        return new NeedsSettings(
                config.getBoolean("needs.enabled", false),
                Math.max(20L, config.getLong("needs.hunger-decay-ticks-per-point", 1200L)),
                Math.max(20L, config.getLong("needs.thirst-decay-ticks-per-point", 800L)),
                Math.clamp(config.getLong("needs.max-managed-delta-ticks", 1200L), 20L, 24_000L),
                Math.max(200L, config.getLong("needs.save-interval-ticks", 1200L)));
    }
}
