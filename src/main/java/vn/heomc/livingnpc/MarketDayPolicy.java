package vn.heomc.livingnpc;

final class MarketDayPolicy {
    private static final long DAY_TICKS = 24_000L;

    private MarketDayPolicy() {
    }

    static boolean open(long fullTime, SeasonFiveSettings settings) {
        if (!settings.enabled()) return true;
        long day = Math.floorDiv(fullTime, DAY_TICKS);
        long time = Math.floorMod(fullTime, DAY_TICKS);
        long eventDay = settings.marketStartTick() > settings.marketEndTick() && time < settings.marketEndTick()
                ? day - 1L : day;
        if (Math.floorMod(eventDay - settings.marketDayOffset(), settings.marketDayInterval()) != 0L) return false;
        if (settings.marketStartTick() == settings.marketEndTick()) return true;
        if (settings.marketStartTick() < settings.marketEndTick()) {
            return time >= settings.marketStartTick() && time < settings.marketEndTick();
        }
        return time >= settings.marketStartTick() || time < settings.marketEndTick();
    }
}
