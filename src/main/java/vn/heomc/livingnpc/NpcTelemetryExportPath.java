package vn.heomc.livingnpc;

import java.io.File;
import java.nio.file.Path;

final class NpcTelemetryExportPath {
    private NpcTelemetryExportPath() {
    }

    static File resolve(File dataFolder, String configuredPath) {
        String path = configuredPath == null || configuredPath.isBlank()
                ? "telemetry/latest.json" : configuredPath.replace('\\', '/');
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("telemetry export path must be relative to the plugin data folder");
        }
        Path relative = Path.of(path).normalize();
        if (relative.isAbsolute() || relative.startsWith("..") || relative.toString().isBlank()) {
            throw new IllegalArgumentException("telemetry export path must stay inside the plugin data folder");
        }
        String fileName = relative.getFileName() == null ? "" : relative.getFileName().toString();
        if (!fileName.endsWith(".json") || fileName.contains(".tmp")) {
            throw new IllegalArgumentException("telemetry export path must name a .json file and not a temporary file");
        }
        Path root = dataFolder.toPath().toAbsolutePath().normalize();
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("telemetry export path must stay inside the plugin data folder");
        }
        return resolved.toFile();
    }
}
