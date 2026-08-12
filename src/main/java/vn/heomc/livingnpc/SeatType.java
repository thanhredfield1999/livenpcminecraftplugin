package vn.heomc.livingnpc;

enum SeatType {
    REST,
    DINING;

    static SeatType parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
