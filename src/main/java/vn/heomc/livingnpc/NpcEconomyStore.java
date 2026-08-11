package vn.heomc.livingnpc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class NpcEconomyStore {
    private static final int MAX_JOURNAL_ENTRIES = 500;
    private final File file;
    private final Logger logger;
    private final Map<UUID, NpcAccount> accounts = new LinkedHashMap<>();
    private final List<Map<String, Object>> journal = new ArrayList<>();

    NpcEconomyStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "economy.yml");
        this.logger = logger;
        load();
    }

    NpcAccount account(UUID npcUuid) {
        return accounts.computeIfAbsent(npcUuid, NpcAccount::new);
    }

    java.util.Collection<NpcAccount> accounts() {
        return List.copyOf(accounts.values());
    }

    boolean remove(UUID npcUuid) {
        accounts.remove(npcUuid);
        journal.removeIf(entry -> npcUuid.toString().equals(entry.get("npc")));
        return save();
    }

    void recordSale(UUID npcUuid, SaleResult sale) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", sale.transactionId());
        entry.put("npc", npcUuid.toString());
        entry.put("type", "SALE");
        entry.put("items", sale.itemCount());
        entry.put("total-minor", sale.totalMinor());
        entry.put("source", "livingnpc-price-book");
        entry.put("created-at", Instant.now().toString());
        journal.add(entry);
        while (journal.size() > MAX_JOURNAL_ENTRIES) {
            journal.removeFirst();
        }
    }

    void restore(NpcAccount account) {
        accounts.put(account.npcUuid(), account);
    }

    void removeSale(String transactionId) {
        journal.removeIf(entry -> transactionId.equals(entry.get("id")));
    }

    synchronized boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("accounts");
        for (NpcAccount account : accounts.values()) {
            ConfigurationSection section = root.createSection(account.npcUuid().toString());
            section.set("balance-minor", account.balanceMinor());
            section.set("produced-this-shift", account.producedThisShift());
            section.set("shift-key", account.shiftKey());
            section.set("last-sale-shift", account.lastSaleShift());
            for (Map.Entry<String, Integer> item : account.inventory().entrySet()) {
                section.set("inventory." + item.getKey(), item.getValue());
            }
        }
        yaml.set("journal", journal);

        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            logger.severe("Could not save NPC economy: " + exception.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("accounts");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection section = root.getConfigurationSection(key);
                    if (section == null) {
                        continue;
                    }
                    NpcAccount account = account(uuid);
                    account.setBalanceMinor(section.getLong("balance-minor"));
                    account.setProducedThisShift(section.getInt("produced-this-shift"));
                    account.setShiftKey(section.getLong("shift-key", Long.MIN_VALUE));
                    account.setLastSaleShift(section.getLong("last-sale-shift", Long.MIN_VALUE));
                    ConfigurationSection inventory = section.getConfigurationSection("inventory");
                    if (inventory != null) {
                        for (String itemKey : inventory.getKeys(false)) {
                            account.setQuantity(itemKey, inventory.getInt(itemKey));
                        }
                    }
                } catch (IllegalArgumentException exception) {
                    logger.warning("Skipping invalid NPC economy account: " + key);
                }
            }
        }
        for (Map<?, ?> raw : yaml.getMapList("journal")) {
            Map<String, Object> entry = new LinkedHashMap<>();
            raw.forEach((key, value) -> entry.put(String.valueOf(key), value));
            journal.add(entry);
        }
    }
}
