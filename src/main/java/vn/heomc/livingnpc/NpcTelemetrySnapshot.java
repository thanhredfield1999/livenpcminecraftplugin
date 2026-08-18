package vn.heomc.livingnpc;

import java.util.List;

public record NpcTelemetrySnapshot(
        int schemaVersion,
        int capacity,
        long totalRecorded,
        List<NpcTelemetryEvent> events,
        NpcTelemetryEconomySnapshot economy,
        NpcTelemetryVisitors visitors,
        List<NpcTelemetryGate> gates) {
    public NpcTelemetrySnapshot {
        events = events == null ? List.of() : List.copyOf(events);
        gates = gates == null ? List.of() : List.copyOf(gates);
    }

    public NpcTelemetrySnapshot(int schemaVersion, int capacity, long totalRecorded, List<NpcTelemetryEvent> events) {
        this(schemaVersion, capacity, totalRecorded, events, null, null, List.of());
    }

    public NpcTelemetrySnapshot(int schemaVersion, int capacity, long totalRecorded, List<NpcTelemetryEvent> events,
            NpcTelemetryEconomySnapshot economy, NpcTelemetryVisitors visitors) {
        this(schemaVersion, capacity, totalRecorded, events, economy, visitors, List.of());
    }
}
