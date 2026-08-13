package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarketDayPolicyTest {
    @Test
    void disabledSeasonKeepsExistingVisitorSchedule() {
        assertTrue(MarketDayPolicy.open(42_000L, settings(false, 7, 1000L, 12_000L)));
    }

    @Test
    void opensOnlyInsideConfiguredMarketDayWindow() {
        SeasonFiveSettings settings = settings(true, 3, 1000L, 12_000L);

        assertFalse(MarketDayPolicy.open(500L, settings));
        assertTrue(MarketDayPolicy.open(1000L, settings));
        assertFalse(MarketDayPolicy.open(12_000L, settings));
        assertFalse(MarketDayPolicy.open(24_000L + 6000L, settings));
        assertTrue(MarketDayPolicy.open(72_000L + 6000L, settings));
    }

    @Test
    void supportsMarketWindowAcrossMidnight() {
        SeasonFiveSettings settings = settings(true, 3, 18_000L, 2000L);

        assertTrue(MarketDayPolicy.open(19_000L, settings));
        assertTrue(MarketDayPolicy.open(24_000L + 1000L, settings));
        assertFalse(MarketDayPolicy.open(6000L, settings));
        assertFalse(MarketDayPolicy.open(24_000L + 19_000L, settings));
    }

    private SeasonFiveSettings settings(boolean enabled, int interval, long start, long end) {
        return new SeasonFiveSettings(enabled, interval, 0, start, end, 1, 2, 0.5, 2.0);
    }
}
