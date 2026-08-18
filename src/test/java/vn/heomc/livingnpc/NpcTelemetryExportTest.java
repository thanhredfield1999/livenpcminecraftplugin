package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NpcTelemetryExportTest {
    @TempDir
    Path tempDir;

    @Test
    void configDefaultsDisableExporterAndUseTelemetryLatestJson() {
        LivingNpcConfig config = LivingNpcConfig.load(new YamlConfiguration());

        assertFalse(config.telemetryExport().enabled());
        assertFalse(config.telemetryExport().economyEnabled());
        assertFalse(config.telemetryExport().visitorsEnabled());
        assertEquals("telemetry/latest.json", config.telemetryExport().file());
        assertEquals(100L, config.telemetryExport().intervalTicks());
    }

    @Test
    void configEnablesEconomyAndVisitorTelemetryIndependently() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("telemetry.economy.enabled", true);
        yaml.set("telemetry.visitors.enabled", true);

        TelemetryExportSettings settings = LivingNpcConfig.load(yaml).telemetryExport();

        assertTrue(settings.economyEnabled());
        assertTrue(settings.visitorsEnabled());
    }

    @Test
    void configClampsExporterIntervalAndRejectsUnsafePath() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("telemetry.export.enabled", true);
        yaml.set("telemetry.export.interval-ticks", 1L);
        yaml.set("telemetry.export.file", "../latest.json");

        LivingNpcConfig config = LivingNpcConfig.load(yaml);

        assertTrue(config.telemetryExport().enabled());
        assertEquals(20L, config.telemetryExport().intervalTicks());
        assertThrows(IllegalArgumentException.class,
                () -> NpcTelemetryExportPath.resolve(tempDir.toFile(), config.telemetryExport().file()));
    }

    @Test
    void exporterWritesBoundedJsonWithAtomicSiblingTemporaryFile() throws Exception {
        NpcTelemetryBuffer buffer = new NpcTelemetryBuffer(1);
        buffer.record(event("old", 1L));
        buffer.record(event("new", 2L));
        NpcTelemetryExporter exporter = new NpcTelemetryExporter(
                tempDir.toFile(), "telemetry/latest.json", Runnable::run, Logger.getAnonymousLogger());

        exporter.writeSnapshot(NpcTelemetryJson.toJson(buffer.snapshot()));

        Path target = tempDir.resolve("telemetry/latest.json");
        assertTrue(Files.exists(target));
        String json = Files.readString(target);
        assertTrue(json.contains("\"capacity\":1"));
        assertTrue(json.contains("\"totalRecorded\":2"));
        assertTrue(json.contains("\"type\":\"new\""));
        assertFalse(json.contains("\"type\":\"old\""));
        assertFalse(Files.exists(tempDir.resolve("telemetry/latest.json.tmp")));
    }

    @Test
    void rejectedExecutorDoesNotStallFutureExports() {
        AtomicBoolean reject = new AtomicBoolean(true);
        Executor executor = command -> {
            if (reject.get()) throw new java.util.concurrent.RejectedExecutionException("closed");
            command.run();
        };
        NpcTelemetryExporter exporter = new NpcTelemetryExporter(
                tempDir.toFile(), "telemetry/latest.json", executor, Logger.getAnonymousLogger());

        assertFalse(exporter.exportSnapshot("{}"));
        assertFalse(exporter.status().writeQueued());
        reject.set(false);
        assertTrue(exporter.exportSnapshot("{}"));
    }

    @Test
    void cancelledExporterDoesNotWriteSnapshot() {
        NpcTelemetryExporter exporter = new NpcTelemetryExporter(
                tempDir.toFile(), "telemetry/latest.json", Runnable::run, Logger.getAnonymousLogger());
        exporter.cancel();

        exporter.writeSnapshot("{}");

        assertFalse(Files.exists(tempDir.resolve("telemetry/latest.json")));
    }

    @Test
    void exporterStatusTracksLastWriteAndCancellation() {
        AtomicBoolean queued = new AtomicBoolean(false);
        Executor executor = command -> queued.set(true);
        NpcTelemetryExporter exporter = new NpcTelemetryExporter(
                tempDir.toFile(), "telemetry/latest.json", executor, Logger.getAnonymousLogger());

        assertEquals("never", exporter.status().lastWriteStatus());
        assertTrue(exporter.exportSnapshot("{}"));
        assertTrue(queued.get());

        exporter.cancel();

        assertFalse(exporter.exportSnapshot("{}"));
        assertTrue(exporter.status().cancelled());
    }

    @Test
    void concurrentExporterInstancesDoNotShareTemporaryFile() throws Exception {
        NpcTelemetryExporter first = new NpcTelemetryExporter(
                tempDir.toFile(), "telemetry/latest.json", Runnable::run, Logger.getAnonymousLogger());
        NpcTelemetryExporter second = new NpcTelemetryExporter(
                tempDir.toFile(), "telemetry/latest.json", Runnable::run, Logger.getAnonymousLogger());

        Thread left = new Thread(() -> first.exportSnapshot("{\"writer\":\"first\"}"));
        Thread right = new Thread(() -> second.exportSnapshot("{\"writer\":\"second\"}"));
        left.start();
        right.start();
        left.join();
        right.join();

        assertTrue(Files.exists(tempDir.resolve("telemetry/latest.json")));
        try (var files = Files.list(tempDir.resolve("telemetry"))) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    private static NpcTelemetryEvent event(String type, long tick) {
        UUID uuid = UUID.randomUUID();
        NpcTelemetryPosition npc = new NpcTelemetryPosition("world", 1, 64, 2, 1.25, 64.0, 2.75, 90.0f, 0.0f);
        NpcTelemetryPosition target = new NpcTelemetryPosition("world", 5, 64, 6, 5.5, 64.0, 6.5, 0.0f, 0.0f);
        NpcTelemetryNavigation navigation = new NpcTelemetryNavigation(true, "world", target, "AStarNavigationStrategy", "present", "[VillageRouteExaminer]", "CITIZENS", 102.0f, -1, 1.5, 1.5, "COMPLETED", 20L);
        NpcTelemetryBlockProbe obstacle = NpcTelemetryBlockProbe.classify(
                "front", "world", 2, 64, 2, Material.STONE, true, false, true);
        return new NpcTelemetryEvent(1, type, uuid, "Steve", "farmer", "world", npc, target,
                "GOING_TO_PLOT", "GOING_TO_PLOT", navigation, "present", obstacle,
                new NpcTelemetrySemanticPoint("PLOT", "plot", "world", target), List.of(obstacle), tick, 123456789L);
    }
}
