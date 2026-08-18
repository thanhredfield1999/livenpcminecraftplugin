package vn.heomc.livingnpc.bluemap;

import org.bukkit.configuration.file.FileConfiguration;

public record BlueMapSettings(boolean enabled, long intervalTicks, long staleTicks) {
    public static BlueMapSettings load(FileConfiguration config) {
        return new BlueMapSettings(
                config.getBoolean("bluemap.markers.enabled", false),
                Math.max(20L, config.getLong("bluemap.markers.interval-ticks", 100L)),
                Math.max(20L, config.getLong("bluemap.markers.stale-ticks", 600L)));
    }
}
