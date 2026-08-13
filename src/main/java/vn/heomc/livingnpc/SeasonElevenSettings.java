package vn.heomc.livingnpc;

import java.util.Map;

record SeasonElevenSettings(
        boolean enabled,
        int daysPerSeason,
        long startDay,
        Map<EconomicSeason, SeasonalEconomyModifiers> modifiers) {

    SeasonElevenSettings {
        modifiers = Map.copyOf(modifiers);
    }
}
