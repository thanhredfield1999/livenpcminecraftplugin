package vn.heomc.livingnpc;

record NpcTelemetryExportStatus(
        boolean enabled,
        String path,
        long lastWriteMillis,
        long lastWriteBytes,
        String lastWriteStatus,
        boolean writeQueued,
        boolean cancelled) {
    static NpcTelemetryExportStatus disabled(String path) {
        return new NpcTelemetryExportStatus(false, path, 0L, 0L, "disabled", false, false);
    }
}
