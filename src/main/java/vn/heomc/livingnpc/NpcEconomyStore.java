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
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class NpcEconomyStore {
    private static final int MAX_JOURNAL_ENTRIES = 500;
    private static final int SCHEMA_VERSION = 3;
    private final File file;
    private final Logger logger;
    private final Map<UUID, NpcAccount> accounts = new LinkedHashMap<>();
    private final List<Map<String, Object>> journal = new ArrayList<>();
    private final java.util.Set<String> transactionIds = new java.util.HashSet<>();
    private boolean writable = true;

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

    boolean writable() {
        return writable;
    }

    boolean remove(UUID npcUuid) {
        NpcAccount removedAccount = accounts.remove(npcUuid);
        List<Map<String, Object>> previousJournal = new ArrayList<>(journal);
        java.util.Set<String> previousTransactions = new java.util.HashSet<>(transactionIds);
        journal.removeIf(entry -> npcUuid.toString().equals(entry.get("npc")));
        if (save()) return true;
        if (removedAccount != null) accounts.put(npcUuid, removedAccount);
        journal.clear();
        journal.addAll(previousJournal);
        transactionIds.clear();
        transactionIds.addAll(previousTransactions);
        return false;
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
        transactionIds.add(sale.transactionId());
        trimJournal();
    }

    void restore(NpcAccount account) {
        accounts.put(account.npcUuid(), account);
    }

    void removeSale(String transactionId) {
        journal.removeIf(entry -> transactionId.equals(entry.get("id")));
        transactionIds.remove(transactionId);
    }

    boolean hasTransaction(String transactionId) {
        return transactionIds.contains(transactionId);
    }

    void recordVisitorSale(UUID villageAccountUuid, String transactionId, int items, long totalMinor) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", transactionId);
        entry.put("npc", villageAccountUuid.toString());
        entry.put("type", "VISITOR_SALE");
        entry.put("items", items);
        entry.put("total-minor", totalMinor);
        entry.put("source", "temporary-visitor");
        entry.put("created-at", Instant.now().toString());
        journal.add(entry);
        transactionIds.add(transactionId);
        trimJournal();
    }

    void recordActivity(NpcActivity activity) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("npc", activity.npcUuid().toString());
        entry.put("village", activity.villageId());
        entry.put("type", "ACTIVITY");
        entry.put("role", activity.role().storageKey());
        entry.put("action", activity.action());
        entry.put("item", activity.itemKey());
        entry.put("amount", activity.amount());
        entry.put("created-at", activity.createdAt().toString());
        journal.add(entry);
        trimJournal();
    }

    List<NpcActivity> activities(String villageId, ResidentRole role, int limit) {
        List<NpcActivity> result = new ArrayList<>();
        for (int index = journal.size() - 1; index >= 0 && result.size() < limit; index--) {
            Map<String, Object> entry = journal.get(index);
            if (!"ACTIVITY".equals(entry.get("type")) || !java.util.Objects.equals(villageId, entry.get("village"))) continue;
            ResidentRole entryRole = ResidentRole.parse(String.valueOf(entry.get("role")));
            if (entryRole == null || role != null && entryRole != role) continue;
            try {
                result.add(new NpcActivity(
                        UUID.fromString(String.valueOf(entry.get("npc"))), villageId, entryRole,
                        String.valueOf(entry.get("action")), String.valueOf(entry.get("item")),
                        entry.get("amount") instanceof Number number ? number.intValue() : 0,
                        Instant.parse(String.valueOf(entry.get("created-at")))));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy journal rows.
            }
        }
        return List.copyOf(result);
    }

    long totalEarnedMinor(UUID accountUuid) {
        return journalTotal(accountUuid, "SALE") + journalTotal(accountUuid, "VISITOR_SALE");
    }

    long totalSpentMinor(UUID accountUuid) {
        return journalTotal(accountUuid, "EXPENSE");
    }

    private long journalTotal(UUID accountUuid, String type) {
        long total = 0L;
        for (Map<String, Object> entry : journal) {
            if (!type.equals(entry.get("type")) || !String.valueOf(accountUuid).equals(entry.get("npc"))) continue;
            Object raw = entry.get("total-minor");
            if (!(raw instanceof Number number) || number.longValue() <= 0L) continue;
            total = saturatedAdd(total, number.longValue());
        }
        return total;
    }

    private static long saturatedAdd(long left, long right) {
        if (right <= 0L || Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    NpcActivity latestActivity(UUID npcUuid) {
        for (int index = journal.size() - 1; index >= 0; index--) {
            Map<String, Object> entry = journal.get(index);
            if (!"ACTIVITY".equals(entry.get("type"))
                    || !npcUuid.toString().equals(String.valueOf(entry.get("npc")))) continue;
            ResidentRole role = ResidentRole.parse(String.valueOf(entry.get("role")));
            if (role == null) return null;
            try {
                return new NpcActivity(
                        npcUuid, String.valueOf(entry.get("village")), role,
                        String.valueOf(entry.get("action")), String.valueOf(entry.get("item")),
                        entry.get("amount") instanceof Number number ? number.intValue() : 0,
                        Instant.parse(String.valueOf(entry.get("created-at"))));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private void trimJournal() {
        while (journal.size() > MAX_JOURNAL_ENTRIES) {
            Map<String, Object> removed = journal.removeFirst();
            Object id = removed.get("id");
            if (id != null && !String.valueOf(id).isBlank()) transactionIds.remove(String.valueOf(id));
        }
    }

    synchronized boolean save() {
        if (!writable) {
            logger.severe("Refusing to overwrite economy.yml after a load failure.");
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        ConfigurationSection root = yaml.createSection("accounts");
        for (NpcAccount account : accounts.values()) {
            ConfigurationSection section = root.createSection(account.npcUuid().toString());
            section.set("balance-minor", account.balanceMinor());
            section.set("produced-this-shift", account.producedThisShift());
            section.set("shift-key", account.shiftKey());
            section.set("last-sale-shift", account.lastSaleShift());
            account.roleProduction().forEach((role, amount) -> section.set("role-production." + role, amount));
            for (Map.Entry<String, Integer> item : account.inventory().entrySet()) {
                section.set("inventory." + item.getKey(), item.getValue());
            }
        }
        yaml.set("journal", journal);

        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        long startNanos = System.nanoTime();
        try {
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            SaveTelemetry.record(logger, "economy.yml", startNanos, file.length());
            return true;
        } catch (IOException exception) {
            logger.severe("Could not save NPC economy: " + exception.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (IOException | InvalidConfigurationException exception) {
                writable = false;
                logger.severe("Could not load economy.yml; writes are disabled to preserve the file: "
                        + exception.getMessage());
                return;
            }
        }
        Integer loadedSchemaVersion = schemaVersion(yaml);
        if (loadedSchemaVersion == null) {
            writable = false;
            logger.severe("economy.yml has an invalid schema-version; writes are disabled.");
            return;
        }
        if (loadedSchemaVersion > SCHEMA_VERSION) {
            writable = false;
            logger.severe("economy.yml uses unsupported schema version " + loadedSchemaVersion
                    + "; this plugin supports up to " + SCHEMA_VERSION + ". Writes are disabled.");
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("accounts");
        boolean resetLegacyQuota = loadedSchemaVersion < 2;
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
                    account.setProducedThisShift(resetLegacyQuota ? 0 : section.getInt("produced-this-shift"));
                    account.setShiftKey(section.getLong("shift-key", Long.MIN_VALUE));
                    account.setLastSaleShift(section.getLong("last-sale-shift", Long.MIN_VALUE));
                    ConfigurationSection roleProduction = section.getConfigurationSection("role-production");
                    if (roleProduction != null) {
                        for (String role : roleProduction.getKeys(false)) {
                            account.setRoleProduction(role, roleProduction.getInt(role));
                        }
                    } else if (loadedSchemaVersion < 3 && account.producedThisShift() > 0) {
                        // Fail closed for the one migration shift; the next shift resets all role counters.
                        account.setRoleProduction(ResidentRole.FISHER.storageKey(), account.producedThisShift());
                    }
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
            Object id = entry.get("id");
            String type = String.valueOf(entry.get("type"));
            if (id != null && !String.valueOf(id).isBlank()
                    && (type.equals("SALE") || type.equals("VISITOR_SALE"))) {
                transactionIds.add(String.valueOf(id));
            }
        }
        trimJournal();
        if (file.exists() && loadedSchemaVersion < SCHEMA_VERSION) {
            save();
        }
    }

    private Integer schemaVersion(YamlConfiguration yaml) {
        if (!yaml.contains("schema-version")) return 1;
        Object value = yaml.get("schema-version");
        if (!(value instanceof Number number)) return null;
        double version = number.doubleValue();
        return Double.isFinite(version) && version == Math.rint(version)
                && version > 0 && version <= Integer.MAX_VALUE ? (int) version : null;
    }
}
