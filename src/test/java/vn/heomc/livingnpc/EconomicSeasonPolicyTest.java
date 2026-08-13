package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EconomicSeasonPolicyTest {
    @Test
    void keepsFoundationClosedWhenDisabled() {
        assertNull(EconomicSeasonPolicy.current(0L, settings(false, 7, 0L)));
    }

    @Test
    void advancesThroughFourSeasonsUsingFullWorldDays() {
        SeasonElevenSettings settings = settings(true, 7, 0L);

        assertEquals(EconomicSeason.SPRING, snapshotAtDay(0, settings).season());
        assertEquals(EconomicSeason.SUMMER, snapshotAtDay(7, settings).season());
        assertEquals(EconomicSeason.AUTUMN, snapshotAtDay(14, settings).season());
        assertEquals(EconomicSeason.WINTER, snapshotAtDay(21, settings).season());
        assertEquals("1:spring", snapshotAtDay(28, settings).id());
    }

    @Test
    void honorsConfiguredStartDayWithoutChangingBeforeStartBehavior() {
        SeasonElevenSettings settings = settings(true, 3, 10L);

        EconomicSeasonSnapshot beforeStart = snapshotAtDay(9, settings);
        EconomicSeasonSnapshot firstDay = snapshotAtDay(10, settings);

        assertEquals(EconomicSeason.WINTER, beforeStart.season());
        assertEquals(-1L, beforeStart.cycle());
        assertEquals(EconomicSeason.SPRING, firstDay.season());
        assertEquals(0, firstDay.dayInSeason());
    }

    @Test
    void exposesImmutableSeasonModifiersWithoutMutatingBaseValues() {
        EconomicSeasonSnapshot winter = snapshotAtDay(21, settings(true, 7, 0L));

        assertEquals(15, winter.modifiers().adjustStockTarget(10));
        assertEquals(8, winter.modifiers().adjustExportDemand(10));
        assertEquals(12, winter.modifiers().adjustLaborPriority(10));
        assertEquals(10, new SeasonalEconomyModifiers(100, 100, 100).adjustStockTarget(10));
    }

    private EconomicSeasonSnapshot snapshotAtDay(long day, SeasonElevenSettings settings) {
        return EconomicSeasonPolicy.current(day * 24_000L, settings);
    }

    private SeasonElevenSettings settings(boolean enabled, int daysPerSeason, long startDay) {
        SeasonalEconomyModifiers normal = new SeasonalEconomyModifiers(100, 100, 100);
        return new SeasonElevenSettings(enabled, daysPerSeason, startDay, Map.of(
                EconomicSeason.SPRING, normal,
                EconomicSeason.SUMMER, normal,
                EconomicSeason.AUTUMN, new SeasonalEconomyModifiers(120, 110, 110),
                EconomicSeason.WINTER, new SeasonalEconomyModifiers(150, 80, 120)));
    }
}
