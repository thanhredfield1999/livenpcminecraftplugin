package vn.heomc.livingnpc;

final class MealSchedulePolicy {
    private static final long DAY_TICKS = 24_000L;

    private MealSchedulePolicy() {
    }

    static MealOpportunity current(long fullTime, SeasonTenSettings settings) {
        if (!settings.enabled()) return null;
        MealOpportunity breakfast = inWindow(
                fullTime, MealPeriod.BREAKFAST, settings.breakfastStartTick(), settings.breakfastEndTick());
        if (breakfast != null) return breakfast;
        MealOpportunity lunch = inWindow(
                fullTime, MealPeriod.LUNCH, settings.lunchStartTick(), settings.lunchEndTick());
        if (lunch != null) return lunch;
        return inWindow(fullTime, MealPeriod.DINNER, settings.dinnerStartTick(), settings.dinnerEndTick());
    }

    private static MealOpportunity inWindow(long fullTime, MealPeriod period, long start, long end) {
        long day = Math.floorDiv(fullTime, DAY_TICKS);
        long time = Math.floorMod(fullTime, DAY_TICKS);
        if (start == end) return null;
        if (start < end) {
            return time >= start && time < end ? new MealOpportunity(period, day) : null;
        }
        if (time >= start) return new MealOpportunity(period, day);
        return time < end ? new MealOpportunity(period, day - 1L) : null;
    }
}
