package vn.heomc.livingnpc;

record SeatValidation(boolean valid, SeatDefinition seat, String reason) {
    static SeatValidation valid(SeatDefinition seat) {
        return new SeatValidation(true, seat, "");
    }

    static SeatValidation invalid(String reason) {
        return new SeatValidation(false, null, reason);
    }
}
