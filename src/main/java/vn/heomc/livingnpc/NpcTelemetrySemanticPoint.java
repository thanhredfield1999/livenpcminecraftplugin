package vn.heomc.livingnpc;

public record NpcTelemetrySemanticPoint(
        String type,
        String name,
        String world,
        NpcTelemetryPosition position) {
}
