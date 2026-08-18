package vn.heomc.livingnpc;

/** Immutable state of one configured navigation gate sampled on Paper main thread. */
public record NpcTelemetryGate(
        String id,
        String world,
        int x,
        int y,
        int z,
        String material,
        Boolean open,
        String status,
        String action,
        long timestampTick) {
    public NpcTelemetryGate {
        id = id == null ? "" : id;
        world = world == null ? "" : world;
        material = material == null || material.isBlank() ? null : material;
        status = status == null || status.isBlank() ? "UNKNOWN" : status;
        action = action == null || action.isBlank() ? null : action;
    }
}
