package vn.heomc.livingnpc;

record EconomicSeasonSnapshot(
        String id,
        EconomicSeason season,
        long cycle,
        int dayInSeason,
        SeasonalEconomyModifiers modifiers) {
}
