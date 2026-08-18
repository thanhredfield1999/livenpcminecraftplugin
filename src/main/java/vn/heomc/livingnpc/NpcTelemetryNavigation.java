package vn.heomc.livingnpc;

public record NpcTelemetryNavigation(
        boolean navigating,
        String targetWorld,
        NpcTelemetryPosition target,
        String strategy,
        String path,
        String examiners,
        String pathfinder,
        float range,
        int stationaryTicks,
        double distanceMargin,
        double pathMargin,
        String cancelReason,
        long elapsedTicks) {
}
