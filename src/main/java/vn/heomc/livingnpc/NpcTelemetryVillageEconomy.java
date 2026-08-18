package vn.heomc.livingnpc;

import java.util.List;

public record NpcTelemetryVillageEconomy(
        String villageId, long balanceMinor, String currencyUnit,
        long totalEarnedMinor, long totalSpentMinor,
        List<NpcTelemetryInventoryItem> inventory,
        List<NpcTelemetryRoleProduction> roleProduction,
        List<NpcTelemetryActivity> activities,
        NpcTelemetryPosition center) {
    public static final int MAX_INVENTORY = 32;
    public static final int MAX_ACTIVITIES = 32;

    public NpcTelemetryVillageEconomy {
        villageId = text(villageId, 96);
        currencyUnit = text(currencyUnit, 32);
        inventory = bounded(inventory, MAX_INVENTORY);
        roleProduction = bounded(roleProduction, MAX_INVENTORY);
        activities = bounded(activities, MAX_ACTIVITIES);
    }

    public NpcTelemetryVillageEconomy(
            String villageId, long balanceMinor, String currencyUnit,
            List<NpcTelemetryInventoryItem> inventory,
            List<NpcTelemetryRoleProduction> roleProduction,
            List<NpcTelemetryActivity> activities) {
        this(villageId, balanceMinor, currencyUnit, balanceMinor, 0L, inventory, roleProduction, activities, null);
    }

    public NpcTelemetryVillageEconomy(
            String villageId, long balanceMinor, String currencyUnit,
            List<NpcTelemetryInventoryItem> inventory,
            List<NpcTelemetryRoleProduction> roleProduction,
            List<NpcTelemetryActivity> activities,
            NpcTelemetryPosition center) {
        this(villageId, balanceMinor, currencyUnit, balanceMinor, 0L, inventory, roleProduction, activities, center);
    }

    private static <T> List<T> bounded(List<T> values, int maximum) {
        if (values == null || values.isEmpty()) return List.of();
        return List.copyOf(values.subList(0, Math.min(values.size(), maximum)));
    }

    private static String text(String value, int maximum) {
        if (value == null) return null;
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}