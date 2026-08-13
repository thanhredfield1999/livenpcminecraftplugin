package vn.heomc.livingnpc;

record SeasonFiveSettings(
        boolean enabled,
        int marketDayInterval,
        int marketDayOffset,
        long marketStartTick,
        long marketEndTick,
        int followerMin,
        int followerMax,
        double packAnimalChance,
        double formationSpacing) {
}
