package vn.heomc.livingnpc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class ServingLedger {
    enum Audience { RESIDENT, VISITOR }
    enum Status { RESERVED, CONSUMED, RELEASED }

    record Reservation(String id, String batchId, UUID npcUuid, Audience audience, Status status) {
    }

    private static final class Batch {
        private final String id;
        private int available;
        private int visitorAvailable;

        private Batch(String id, int servings, int visitorQuota) {
            this.id = id;
            this.available = servings;
            this.visitorAvailable = Math.min(servings, visitorQuota);
        }
    }

    private final Map<String, Batch> batches = new LinkedHashMap<>();
    private final Map<String, Reservation> reservations = new LinkedHashMap<>();
    private final Map<UUID, String> activeByNpc = new LinkedHashMap<>();

    boolean publish(String batchId, int servings, int visitorQuota) {
        if (batchId == null || batchId.isBlank() || servings <= 0 || visitorQuota < 0
                || batches.containsKey(batchId)) return false;
        batches.put(batchId, new Batch(batchId, servings, visitorQuota));
        return true;
    }

    Reservation reserve(String reservationId, String batchId, UUID npcUuid, Audience audience) {
        if (reservationId == null || reservationId.isBlank() || npcUuid == null || audience == null) return null;
        Reservation existing = reservations.get(reservationId);
        if (existing != null) return existing;
        if (activeByNpc.containsKey(npcUuid)) return null;
        Batch batch = batches.get(batchId);
        if (batch == null || batch.available <= 0
                || audience == Audience.VISITOR && batch.visitorAvailable <= 0) return null;
        batch.available--;
        if (audience == Audience.VISITOR) batch.visitorAvailable--;
        Reservation reservation = new Reservation(reservationId, batch.id, npcUuid, audience, Status.RESERVED);
        reservations.put(reservationId, reservation);
        activeByNpc.put(npcUuid, reservationId);
        return reservation;
    }

    Reservation consume(String reservationId) {
        Reservation current = reservations.get(reservationId);
        if (current == null || current.status() != Status.RESERVED) return current;
        Reservation consumed = withStatus(current, Status.CONSUMED);
        reservations.put(reservationId, consumed);
        activeByNpc.remove(current.npcUuid(), reservationId);
        return consumed;
    }

    Reservation release(String reservationId) {
        Reservation current = reservations.get(reservationId);
        if (current == null || current.status() != Status.RESERVED) return current;
        Batch batch = batches.get(current.batchId());
        if (batch != null) {
            batch.available++;
            if (current.audience() == Audience.VISITOR) batch.visitorAvailable++;
        }
        Reservation released = withStatus(current, Status.RELEASED);
        reservations.put(reservationId, released);
        activeByNpc.remove(current.npcUuid(), reservationId);
        return released;
    }

    int available(String batchId) {
        Batch batch = batches.get(batchId);
        return batch == null ? 0 : batch.available;
    }

    private Reservation withStatus(Reservation current, Status status) {
        return new Reservation(current.id(), current.batchId(), current.npcUuid(), current.audience(), status);
    }
}
