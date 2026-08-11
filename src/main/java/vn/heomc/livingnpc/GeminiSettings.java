package vn.heomc.livingnpc;

import java.util.EnumSet;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

record GeminiSettings(
        boolean enabled,
        String model,
        int globalRequestsPerMinute,
        long perNpcCooldownSeconds,
        double monthlyBudgetUsd,
        EnumSet<GeminiIntent> allowedIntents) {

    static GeminiSettings load(FileConfiguration config) {
        EnumSet<GeminiIntent> allowed = EnumSet.noneOf(GeminiIntent.class);
        for (String value : config.getStringList("gemini.allowed-intents")) {
            try {
                allowed.add(GeminiIntent.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Unknown values stay unavailable rather than becoming arbitrary actions.
            }
        }
        double budget = Math.max(0.0, config.getDouble("gemini.monthly-budget-usd", 0.0));
        boolean enabled = config.getBoolean("gemini.enabled", false)
                && budget > 0.0
                && System.getenv("GEMINI_API_KEY") != null;
        return new GeminiSettings(
                enabled,
                config.getString("gemini.model", "gemini-2.5-flash-lite"),
                Math.clamp(config.getInt("gemini.global-requests-per-minute", 10), 1, 10),
                Math.max(300L, config.getLong("gemini.per-npc-cooldown-seconds", 300L)),
                budget,
                allowed);
    }

    @Override
    public EnumSet<GeminiIntent> allowedIntents() {
        return allowedIntents.clone();
    }
}
