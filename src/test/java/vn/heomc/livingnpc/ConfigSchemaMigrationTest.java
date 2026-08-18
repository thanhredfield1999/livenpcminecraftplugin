package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigSchemaMigrationTest {
    @TempDir
    Path tempDir;

    @Test
    void missingVersionMergesDefaultsPreservesUnknownAndCreatesBackup() throws Exception {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, "custom:\n  value: keep\n");
        YamlConfiguration defaults = defaults();

        assertEquals(ConfigSchemaMigration.Result.MIGRATED,
                ConfigSchemaMigration.migrate(file.toFile(), defaults, logger()));

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(1, loaded.getInt("config-version"));
        assertEquals("keep", loaded.getString("custom.value"));
        assertEquals("default", loaded.getString("new-setting"));
        try (var files = Files.list(tempDir)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("config.yml.bak-")));
        }
    }

    @Test
    void currentVersionDoesNotRewriteOrCreateBackup() throws Exception {
        Path file = tempDir.resolve("config.yml");
        String content = "config-version: 1\ncustom: keep\n";
        Files.writeString(file, content);

        assertEquals(ConfigSchemaMigration.Result.CURRENT,
                ConfigSchemaMigration.migrate(file.toFile(), defaults(), logger()));
        assertEquals(content, Files.readString(file));
        try (var files = Files.list(tempDir)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().contains(".bak-")));
        }
    }

    @Test
    void futureVersionFailsClosedWithoutChangingFile() throws Exception {
        Path file = tempDir.resolve("config.yml");
        String content = "config-version: 99\ncustom: keep\n";
        Files.writeString(file, content);

        assertEquals(ConfigSchemaMigration.Result.UNSUPPORTED,
                ConfigSchemaMigration.migrate(file.toFile(), defaults(), logger()));
        assertEquals(content, Files.readString(file));
    }

    @Test
    void corruptYamlFailsClosedWithoutChangingFile() throws Exception {
        Path file = tempDir.resolve("config.yml");
        String content = "config-version: [not valid\n";
        Files.writeString(file, content);

        assertEquals(ConfigSchemaMigration.Result.INVALID,
                ConfigSchemaMigration.migrate(file.toFile(), defaults(), logger()));
        assertEquals(content, Files.readString(file));
    }

    private static YamlConfiguration defaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("config-version", 1);
        defaults.set("new-setting", "default");
        return defaults;
    }

    private static Logger logger() {
        return Logger.getLogger("ConfigSchemaMigrationTest");
    }
}
