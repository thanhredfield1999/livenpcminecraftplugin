package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class NeedsSettingsTest {
    @Test
    void defaultsToDisabledAndSafeRates() {
        NeedsSettings settings = LivingNpcConfig.load(new YamlConfiguration()).needs();

        assertFalse(settings.enabled());
        assertEquals(1200L, settings.hungerDecayTicksPerPoint());
        assertEquals(800L, settings.thirstDecayTicksPerPoint());
        assertEquals(1200L, settings.maxManagedDeltaTicks());
    }

    @Test
    void clampsUnsafeValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("needs.hunger-decay-ticks-per-point", 0L);
        yaml.set("needs.thirst-decay-ticks-per-point", -1L);
        yaml.set("needs.max-managed-delta-ticks", 100_000L);
        yaml.set("needs.save-interval-ticks", 1L);

        NeedsSettings settings = LivingNpcConfig.load(yaml).needs();

        assertEquals(20L, settings.hungerDecayTicksPerPoint());
        assertEquals(20L, settings.thirstDecayTicksPerPoint());
        assertEquals(24_000L, settings.maxManagedDeltaTicks());
        assertEquals(200L, settings.saveIntervalTicks());
    }
}
