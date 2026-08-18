package vn.heomc.livingnpc;

import java.util.List;
import java.util.UUID;

record NpcTelemetryVisitors(boolean enabled, int maxActive, int activeCount, List<NpcTelemetryVisitor> active) {
    static final int MAX_ACTIVE = 16;

    NpcTelemetryVisitors {
        maxActive = Math.max(0, maxActive);
        activeCount = Math.max(0, activeCount);
        if (active == null || active.isEmpty()) active = List.of();
        else active = List.copyOf(active.subList(0, Math.min(active.size(), MAX_ACTIVE)));
    }
}

record NpcTelemetryVisitor(
        UUID id, String name, String villageId, String role, String phase, long walletMinor,
        List<NpcTelemetryInventoryItem> demand, NpcTelemetryPosition target, String purchaseStatus) {
    NpcTelemetryVisitor {
        name = text(name, 96);
        villageId = text(villageId, 96);
        role = text(role, 64);
        phase = text(phase, 64);
        walletMinor = Math.max(0L, walletMinor);
        if (demand == null || demand.isEmpty()) demand = List.of();
        else demand = List.copyOf(demand.subList(0, Math.min(demand.size(), 8)));
        purchaseStatus = text(purchaseStatus, 64);
    }

    private static String text(String value, int maximum) {
        if (value == null) return null;
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
