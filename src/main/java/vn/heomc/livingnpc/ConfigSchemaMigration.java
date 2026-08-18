package vn.heomc.livingnpc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Version gate and additive migration for plugin data config.yml. */
final class ConfigSchemaMigration {
    static final String VERSION_KEY = "config-version";
    static final int CURRENT_VERSION = 1;

    private ConfigSchemaMigration() {
    }

    static Result migrate(File file, YamlConfiguration defaults, Logger logger) {
        YamlConfiguration live = new YamlConfiguration();
        try {
            if (file.exists()) live.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            logger.log(Level.SEVERE, "Could not load config.yml; config-dependent runtime is disabled to preserve the file.", exception);
            return Result.INVALID;
        }

        Integer version = version(live);
        if (version == null) {
            logger.severe("config.yml has invalid config-version; config-dependent runtime is disabled to preserve the file.");
            return Result.INVALID;
        }
        if (version > CURRENT_VERSION) {
            logger.severe("config.yml uses unsupported config-version " + version
                    + "; this plugin supports up to " + CURRENT_VERSION
                    + ". Config-dependent runtime is disabled; file will not be changed.");
            return Result.UNSUPPORTED;
        }
        if (version == CURRENT_VERSION) return Result.CURRENT;

        mergeMissing(live, defaults, "");
        live.set(VERSION_KEY, CURRENT_VERSION);
        if (!backup(file, logger)) {
            logger.severe("Could not create config.yml migration backup; config-dependent runtime is disabled and file was not changed.");
            return Result.INVALID;
        }
        if (!AtomicYamlStore.save(live, file, logger, "migrated config.yml")) {
            logger.severe("Could not write migrated config.yml; config-dependent runtime is disabled.");
            return Result.INVALID;
        }
        logger.info("Migrated config.yml from config-version " + version + " to " + CURRENT_VERSION
                + "; unknown keys preserved.");
        return Result.MIGRATED;
    }

    private static Integer version(YamlConfiguration yaml) {
        if (!yaml.contains(VERSION_KEY)) return 0;
        Object value = yaml.get(VERSION_KEY);
        if (!(value instanceof Number number)) return null;
        double numeric = number.doubleValue();
        return Double.isFinite(numeric) && numeric == Math.rint(numeric)
                && numeric >= 0 && numeric <= Integer.MAX_VALUE ? (int) numeric : null;
    }

    private static void mergeMissing(ConfigurationSection target, ConfigurationSection defaults, String path) {
        for (String key : defaults.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            Object defaultValue = defaults.get(key);
            if (defaultValue instanceof ConfigurationSection defaultSection) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (target.contains(key) && targetSection == null) continue;
                if (targetSection == null) {
                    target.createSection(key);
                    targetSection = target.getConfigurationSection(key);
                }
                mergeMissing(targetSection, defaultSection, fullPath);
            } else if (!target.contains(key)) {
                target.set(key, defaultValue);
            }
        }
    }

    private static boolean backup(File file, Logger logger) {
        if (!file.exists()) return true;
        File backup = new File(file.getParentFile(), file.getName() + ".bak-" + Instant.now().toEpochMilli());
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            logger.info("Backed up config.yml before migration: " + backup.getName());
            return true;
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Could not back up config.yml before migration.", exception);
            return false;
        }
    }

    enum Result {
        CURRENT,
        MIGRATED,
        UNSUPPORTED,
        INVALID
    }
}
