package vn.heomc.livingnpc;

record TelemetryExportSettings(
        boolean enabled, String file, long intervalTicks, boolean economyEnabled, boolean visitorsEnabled) {
}
