package vn.heomc.livingnpc.bluemap;

public record BlueMapMarkerSpec(
        String id,
        String world,
        BlueMapMarkerPosition position,
        String label,
        String detail,
        boolean semantic,
        Object rawPayload,
        String iconUrl,
        int iconWidth,
        int iconHeight,
        BlueMapMarkerPosition routeTarget,
        String routeColor) {
    public BlueMapMarkerSpec(
            String id, String world, BlueMapMarkerPosition position, String label, String detail,
            boolean semantic, Object rawPayload) {
        this(id, world, position, label, detail, semantic, rawPayload, null, 0, 0, null, null);
    }
}
