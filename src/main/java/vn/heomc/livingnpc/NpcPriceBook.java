package vn.heomc.livingnpc;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class NpcPriceBook {
    private final Map<String, Long> pricesMinor = new LinkedHashMap<>();

    NpcPriceBook(File dataFolder) {
        reload(dataFolder);
    }

    void reload(File dataFolder) {
        pricesMinor.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(dataFolder, "prices.yml"));
        ConfigurationSection prices = yaml.getConfigurationSection("npc-prices");
        if (prices == null) {
            return;
        }
        for (String key : prices.getKeys(false)) {
            BigDecimal value = BigDecimal.valueOf(prices.getDouble(key));
            long minor = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
            if (minor > 0L) {
                pricesMinor.put(normalize(key), minor);
            }
        }
    }

    long priceMinor(String itemKey) {
        return pricesMinor.getOrDefault(normalize(itemKey), 0L);
    }

    private String normalize(String key) {
        return key.toLowerCase(Locale.ROOT).replace("minecraft:", "");
    }
}
