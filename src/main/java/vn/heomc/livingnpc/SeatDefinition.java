package vn.heomc.livingnpc;

record SeatDefinition(String id, StoredLocation location, SeatType type) {
    SeatDefinition {
        if (id == null || id.isBlank() || location == null || type == null) {
            throw new IllegalArgumentException("Seat id, location and type are required");
        }
    }
}
