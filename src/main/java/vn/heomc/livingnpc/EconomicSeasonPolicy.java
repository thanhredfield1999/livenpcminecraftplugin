package vn.heomc.livingnpc;

final class EconomicSeasonPolicy {
    private static final long TICKS_PER_DAY = 24_000L;
    private static final int SEASON_COUNT = EconomicSeason.values().length;

    private EconomicSeasonPolicy() {
    }

    static EconomicSeasonSnapshot current(long fullTime, SeasonElevenSettings settings) {
        if (!settings.enabled()) {
            return null;
        }
        long worldDay = Math.floorDiv(fullTime, TICKS_PER_DAY);
        long elapsedDay = worldDay - settings.startDay();
        long seasonNumber = Math.floorDiv(elapsedDay, settings.daysPerSeason());
        int seasonIndex = Math.floorMod(seasonNumber, SEASON_COUNT);
        EconomicSeason season = EconomicSeason.values()[seasonIndex];
        long cycle = Math.floorDiv(seasonNumber, SEASON_COUNT);
        int dayInSeason = Math.floorMod(elapsedDay, settings.daysPerSeason());
        return new EconomicSeasonSnapshot(
                cycle + ":" + season.name().toLowerCase(),
                season,
                cycle,
                dayInSeason,
                settings.modifiers().get(season));
    }
}
