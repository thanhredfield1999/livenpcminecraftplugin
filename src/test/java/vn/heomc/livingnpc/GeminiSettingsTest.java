package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GeminiSettingsTest {
    @Test
    void zeroBudgetAlwaysDisablesGatewayAndFiltersIntents() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("gemini.enabled", true);
        yaml.set("gemini.monthly-budget-usd", 0.0);
        yaml.set("gemini.allowed-intents", java.util.List.of("WORK", "INVALID", "SOCIALIZE"));

        GeminiSettings settings = GeminiSettings.load(yaml);

        assertFalse(settings.enabled());
        assertTrue(settings.allowedIntents().contains(GeminiIntent.WORK));
        assertTrue(settings.allowedIntents().contains(GeminiIntent.SOCIALIZE));
        assertFalse(settings.allowedIntents().contains(GeminiIntent.VISIT_MARKET));
    }
}
