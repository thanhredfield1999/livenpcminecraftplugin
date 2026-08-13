package vn.heomc.livingnpc;

enum ActivityPointType {
    HOME_EXIT,
    DINING,
    WATER,
    SOCIAL,
    SCENIC,
    REST,
    WORK_BREAK;

    static ActivityPointType parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
