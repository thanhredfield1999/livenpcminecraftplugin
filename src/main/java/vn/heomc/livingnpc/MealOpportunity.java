package vn.heomc.livingnpc;

record MealOpportunity(MealPeriod period, long day) {
    String id() {
        return day + ":" + period.name().toLowerCase(java.util.Locale.ROOT);
    }
}
