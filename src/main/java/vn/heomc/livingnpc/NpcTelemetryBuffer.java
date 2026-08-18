package vn.heomc.livingnpc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

final class NpcTelemetryBuffer {
    private static final int SCHEMA_VERSION = 1;
    private final int capacity;
    private final Deque<NpcTelemetryEvent> events = new ArrayDeque<>();
    private long totalRecorded;

    NpcTelemetryBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    synchronized void record(NpcTelemetryEvent event) {
        if (event == null) return;
        while (events.size() >= capacity) events.removeFirst();
        events.addLast(event);
        totalRecorded++;
    }

    synchronized NpcTelemetrySnapshot snapshot() {
        return new NpcTelemetrySnapshot(SCHEMA_VERSION, capacity, totalRecorded, new ArrayList<>(events));
    }
}
