package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests proving that SaveTelemetry instrumentation does not alter
 * save success/failure behavior, data integrity, or fail-closed semantics
 * in NeedsStore, NpcEconomyStore, and AtomicYamlStore.
 */
class SaveTelemetryTest {
    @TempDir
    Path tempDir;

    // --- NeedsStore: telemetry does not break successful round-trip ---

    @Test
    void needsStoreRoundTripSucceedsWithTelemetry() {
        UUID uuid = UUID.randomUUID();
        Logger logger = captureLogger("needs-success");
        NeedsStore store = new NeedsStore(tempDir.toFile(), logger);
        ResidentNeeds value = new ResidentNeeds(uuid, "StillCliff", 62, 48, 900L, 12L, 34L);

        assertTrue(store.save(Map.of(uuid, value)));

        // Data survives reload
        ResidentNeeds loaded = new NeedsStore(tempDir.toFile(), logger).load().get(uuid);
        assertEquals(62, loaded.hunger());
        assertEquals(48, loaded.thirst());
        assertEquals("StillCliff", loaded.world());
    }

    @Test
    void needsStoreEmitsTelemetryLogOnSuccess() {
        UUID uuid = UUID.randomUUID();
        CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<>();
        Logger logger = captureLogger("needs-log", records);
        NeedsStore store = new NeedsStore(tempDir.toFile(), logger);

        assertTrue(store.save(Map.of(uuid, new ResidentNeeds(uuid, "world", 50, 50))));

        assertTrue(records.stream().anyMatch(record ->
                record.getMessage().startsWith("YAML_SAVE description=needs.yml")));
    }

    @Test
    void needsStoreFailClosedPreservedWithTelemetry() throws Exception {
        File file = tempDir.resolve("needs.yml").toFile();
        Files.writeString(file.toPath(), "residents: [broken");
        NeedsStore store = new NeedsStore(tempDir.toFile(), Logger.getAnonymousLogger());

        assertTrue(store.load().isEmpty());
        assertFalse(store.save(Map.of()));
        assertEquals("residents: [broken", Files.readString(file.toPath()));
    }

    @Test
    void needsStoreFutureSchemaDisablesWritesWithTelemetry() throws Exception {
        File file = tempDir.resolve("needs.yml").toFile();
        String original = "schema-version: 2\nresidents: {}\nfuture-field: keep\n";
        Files.writeString(file.toPath(), original);
        NeedsStore store = new NeedsStore(tempDir.toFile(), Logger.getAnonymousLogger());

        assertTrue(store.load().isEmpty());
        assertFalse(store.save(Map.of()));
        assertEquals(original, Files.readString(file.toPath()));
    }

    // --- NpcEconomyStore: telemetry does not break successful save/fail-closed ---

    @Test
    void economyStoreRoundTripSucceedsWithTelemetry() {
        Logger logger = captureLogger("economy-success");
        NpcEconomyStore store = new NpcEconomyStore(tempDir.toFile(), logger);
        UUID npc = UUID.randomUUID();
        store.account(npc).setBalanceMinor(1234L);

        assertTrue(store.save());

        NpcEconomyStore reloaded = new NpcEconomyStore(tempDir.toFile(), logger);
        assertEquals(1234L, reloaded.account(npc).balanceMinor());
    }

    @Test
    void economyStoreEmitsTelemetryLogOnSuccess() {
        CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<>();
        Logger logger = captureLogger("economy-log", records);
        NpcEconomyStore store = new NpcEconomyStore(tempDir.toFile(), logger);
        store.account(UUID.randomUUID()).setBalanceMinor(100L);

        assertTrue(store.save());

        assertTrue(records.stream().anyMatch(record ->
                record.getMessage().startsWith("YAML_SAVE description=economy.yml")));
    }

    @Test
    void economyStoreFailClosedPreservedWithTelemetry() throws Exception {
        File file = tempDir.resolve("economy.yml").toFile();
        Files.writeString(file.toPath(), "accounts: [broken");
        NpcEconomyStore store = new NpcEconomyStore(tempDir.toFile(), Logger.getAnonymousLogger());

        assertFalse(store.writable());
        assertFalse(store.save());
        assertEquals("accounts: [broken", Files.readString(file.toPath()));
    }

    @Test
    void economyStoreFutureSchemaDisablesWritesWithTelemetry() throws Exception {
        File file = tempDir.resolve("economy.yml").toFile();
        String original = "schema-version: 999\naccounts: {}\n";
        Files.writeString(file.toPath(), original);
        NpcEconomyStore store = new NpcEconomyStore(tempDir.toFile(), Logger.getAnonymousLogger());

        assertFalse(store.writable());
        assertFalse(store.save());
        assertEquals(original, Files.readString(file.toPath()));
    }

    // --- AtomicYamlStore: telemetry does not break shared save path ---

    @Test
    void atomicYamlStoreSuccessWithTelemetry() {
        CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<>();
        Logger logger = captureLogger("atomic-success", records);
        File target = tempDir.resolve("test.yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("key", "value");

        assertTrue(AtomicYamlStore.save(yaml, target, logger, "test.yml"));
        assertTrue(target.exists());

        // Data round-trip
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(target);
        assertEquals("value", loaded.getString("key"));

        // Telemetry emitted
        assertTrue(records.stream().anyMatch(record ->
                record.getMessage().startsWith("YAML_SAVE description=test.yml")));
    }

    @Test
    void atomicYamlStoreTelemetryContainsElapsedAndBytes() {
        CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<>();
        Logger logger = captureLogger("atomic-fields", records);
        File target = tempDir.resolve("metrics.yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("data", "something");

        assertTrue(AtomicYamlStore.save(yaml, target, logger, "metrics.yml"));

        LogRecord telemetry = records.stream()
                .filter(record -> record.getMessage().startsWith("YAML_SAVE"))
                .findFirst().orElse(null);
        assertTrue(telemetry != null, "Expected YAML_SAVE log record");
        assertTrue(telemetry.getMessage().contains("elapsedMicros="));
        assertTrue(telemetry.getMessage().contains("fileBytes="));
    }

    @Test
    void atomicYamlStoreWriteFailureReturnsFalseWithTelemetry() throws Exception {
        // Create a directory where the target file should be — yaml.save() cannot overwrite a directory
        File blocker = tempDir.resolve("blocked.yml.tmp").toFile();
        blocker.mkdirs();
        File target = tempDir.resolve("blocked.yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("key", "value");

        // The .tmp write will fail because blocker is a directory
        assertFalse(AtomicYamlStore.save(yaml, target, Logger.getAnonymousLogger(), "blocked.yml"));
    }

    // --- SaveTelemetry unit: threshold logic ---

    @Test
    void telemetryLogsWarningAboveThreshold() {
        CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<>();
        Logger logger = captureLogger("threshold", records);
        // startNanos well in the past → elapsed > threshold
        long pastNanos = System.nanoTime() - (SaveTelemetry.SLOW_THRESHOLD_MICROS + 1000) * 1_000L;

        SaveTelemetry.record(logger, "slow-file.yml", pastNanos, 4096);

        LogRecord record = records.stream()
                .filter(r -> r.getMessage().contains("slow-file.yml"))
                .findFirst().orElse(null);
        assertTrue(record != null);
        assertEquals(Level.WARNING, record.getLevel());
    }

    @Test
    void telemetryLogsFineUnderThreshold() {
        CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<>();
        Logger logger = captureLogger("fast", records);
        long justNow = System.nanoTime();

        SaveTelemetry.record(logger, "fast-file.yml", justNow, 128);

        LogRecord record = records.stream()
                .filter(r -> r.getMessage().contains("fast-file.yml"))
                .findFirst().orElse(null);
        assertTrue(record != null);
        assertEquals(Level.FINE, record.getLevel());
    }

    // --- Helpers ---

    private static Logger captureLogger(String name) {
        return captureLogger(name, new CopyOnWriteArrayList<>());
    }

    private static Logger captureLogger(String name, CopyOnWriteArrayList<LogRecord> records) {
        Logger logger = Logger.getLogger("SaveTelemetryTest." + name);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        return logger;
    }
}
