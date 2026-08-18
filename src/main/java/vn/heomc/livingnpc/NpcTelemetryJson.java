package vn.heomc.livingnpc;

import java.util.List;
import java.util.Locale;

final class NpcTelemetryJson {
    private NpcTelemetryJson() {
    }

    static String toJson(NpcTelemetrySnapshot snapshot) {
        StringBuilder builder = new StringBuilder(4096);
        builder.append('{');
        field(builder, "schemaVersion", snapshot.schemaVersion()).append(',');
        field(builder, "capacity", snapshot.capacity()).append(',');
        field(builder, "totalRecorded", snapshot.totalRecorded()).append(',');
        builder.append("\"events\":");
        events(builder, snapshot.events());
        builder.append(',').append("\"gates\":");
        gates(builder, snapshot.gates());
        if (snapshot.economy() != null) {
            builder.append(',').append("\"economy\":");
            economy(builder, snapshot.economy());
        }
        if (snapshot.visitors() != null) {
            builder.append(',').append("\"visitors\":");
            visitors(builder, snapshot.visitors());
        }
        builder.append('}');
        return builder.toString();
    }

    private static void events(StringBuilder builder, List<NpcTelemetryEvent> events) {
        builder.append('[');
        for (int index = 0; index < events.size(); index++) {
            if (index > 0) builder.append(',');
            event(builder, events.get(index));
        }
        builder.append(']');
    }

    private static void gates(StringBuilder builder, List<NpcTelemetryGate> gates) {
        builder.append('[');
        for (int index = 0; index < gates.size(); index++) {
            if (index > 0) builder.append(',');
            NpcTelemetryGate gate = gates.get(index);
            builder.append('{');
            field(builder, "id", gate.id()).append(',');
            field(builder, "world", gate.world()).append(',');
            field(builder, "x", gate.x()).append(',');
            field(builder, "y", gate.y()).append(',');
            field(builder, "z", gate.z()).append(',');
            field(builder, "material", gate.material()).append(',');
            field(builder, "open", gate.open()).append(',');
            field(builder, "status", gate.status()).append(',');
            field(builder, "action", gate.action()).append(',');
            field(builder, "timestampTick", gate.timestampTick());
            builder.append('}');
        }
        builder.append(']');
    }

    private static void economy(StringBuilder builder, NpcTelemetryEconomySnapshot economy) {
        builder.append('{').append("\"villages\":[");
        for (int index = 0; index < economy.villages().size(); index++) {
            if (index > 0) builder.append(',');
            villageEconomy(builder, economy.villages().get(index));
        }
        builder.append("]}");
    }

    private static void villageEconomy(StringBuilder builder, NpcTelemetryVillageEconomy village) {
        builder.append('{');
        field(builder, "villageId", village.villageId()).append(',');
        field(builder, "balanceMinor", village.balanceMinor()).append(',');
        field(builder, "currencyUnit", village.currencyUnit()).append(',');
        field(builder, "totalEarnedMinor", village.totalEarnedMinor()).append(',');
        field(builder, "totalSpentMinor", village.totalSpentMinor()).append(',');
        builder.append("\"inventory\":"); inventory(builder, village.inventory()); builder.append(',');
        builder.append("\"roleProduction\":"); roleProduction(builder, village.roleProduction()); builder.append(',');
        builder.append("\"activities\":"); activities(builder, village.activities()); builder.append(',');
        builder.append("\"center\":"); position(builder, village.center());
        builder.append('}');
    }

    private static void inventory(StringBuilder builder, List<NpcTelemetryInventoryItem> inventory) {
        builder.append('[');
        for (int index = 0; index < inventory.size(); index++) {
            if (index > 0) builder.append(',');
            NpcTelemetryInventoryItem item = inventory.get(index);
            builder.append('{'); field(builder, "item", item.item()).append(','); field(builder, "amount", item.amount()); builder.append('}');
        }
        builder.append(']');
    }

    private static void roleProduction(StringBuilder builder, List<NpcTelemetryRoleProduction> production) {
        builder.append('[');
        for (int index = 0; index < production.size(); index++) {
            if (index > 0) builder.append(',');
            NpcTelemetryRoleProduction value = production.get(index);
            builder.append('{'); field(builder, "role", value.role()).append(','); field(builder, "amount", value.amount()); builder.append('}');
        }
        builder.append(']');
    }

    private static void activities(StringBuilder builder, List<NpcTelemetryActivity> activities) {
        builder.append('[');
        for (int index = 0; index < activities.size(); index++) {
            if (index > 0) builder.append(',');
            NpcTelemetryActivity activity = activities.get(index);
            builder.append('{');
            field(builder, "role", activity.role()).append(',');
            field(builder, "action", activity.action()).append(',');
            field(builder, "item", activity.item()).append(',');
            field(builder, "amount", activity.amount()).append(',');
            field(builder, "createdAt", activity.createdAt() == null ? null : activity.createdAt().toString());
            builder.append('}');
        }
        builder.append(']');
    }

    private static void visitors(StringBuilder builder, NpcTelemetryVisitors visitors) {
        builder.append('{');
        field(builder, "enabled", visitors.enabled()).append(',');
        field(builder, "maxActive", visitors.maxActive()).append(',');
        field(builder, "activeCount", visitors.activeCount()).append(',');
        builder.append("\"active\":[");
        for (int index = 0; index < visitors.active().size(); index++) {
            if (index > 0) builder.append(',');
            NpcTelemetryVisitor visitor = visitors.active().get(index);
            builder.append('{');
            field(builder, "id", visitor.id() == null ? null : visitor.id().toString()).append(',');
            field(builder, "name", visitor.name()).append(',');
            field(builder, "villageId", visitor.villageId()).append(',');
            field(builder, "role", visitor.role()).append(',');
            field(builder, "phase", visitor.phase()).append(',');
            field(builder, "walletMinor", visitor.walletMinor()).append(',');
            builder.append("\"demand\":"); inventory(builder, visitor.demand()); builder.append(',');
            builder.append("\"target\":"); position(builder, visitor.target()); builder.append(',');
            field(builder, "purchaseStatus", visitor.purchaseStatus());
            builder.append('}');
        }
        builder.append("]}");
    }

    private static void event(StringBuilder builder, NpcTelemetryEvent event) {
        builder.append('{');
        field(builder, "schemaVersion", event.schemaVersion()).append(',');
        field(builder, "type", event.type()).append(',');
        field(builder, "npcId", event.npcId() == null ? null : event.npcId().toString()).append(',');
        field(builder, "name", event.name()).append(',');
        field(builder, "skinName", event.skinName()).append(',');
        field(builder, "role", event.role()).append(',');
        field(builder, "villageId", event.villageId()).append(',');
        field(builder, "world", event.world()).append(',');
        builder.append("\"npcBlock\":"); position(builder, event.npcBlock()); builder.append(',');
        builder.append("\"npcPrecise\":"); position(builder, event.npcPrecise()); builder.append(',');
        builder.append("\"targetBlock\":"); position(builder, event.targetBlock()); builder.append(',');
        builder.append("\"targetPrecise\":"); position(builder, event.targetPrecise()); builder.append(',');
        field(builder, "state", event.state()).append(',');
        field(builder, "phase", event.phase()).append(',');
        builder.append("\"navigation\":"); navigation(builder, event.navigation()); builder.append(',');
        field(builder, "path", event.path()).append(',');
        builder.append("\"obstacle\":"); blockProbe(builder, event.obstacle()); builder.append(',');
        builder.append("\"semanticPoint\":"); semanticPoint(builder, event.semanticPoint()); builder.append(',');
        builder.append("\"blockProbes\":"); blockProbes(builder, event.blockProbes()); builder.append(',');
        field(builder, "timestampTick", event.timestampTick()).append(',');
        field(builder, "timestampMillis", event.timestampMillis());
        if (event.account() != null) {
            builder.append(',').append("\"account\":");
            account(builder, event.account());
        }
        builder.append('}');
    }

    private static void account(StringBuilder builder, NpcTelemetryAccount account) {
        builder.append('{');
        field(builder, "balanceMinor", account.balanceMinor()).append(',');
        field(builder, "currencyUnit", account.currencyUnit()).append(',');
        field(builder, "inventoryTotal", account.inventoryTotal()).append(',');
        builder.append("\"inventory\":"); inventory(builder, account.inventory());
        builder.append('}');
    }

    private static void position(StringBuilder builder, NpcTelemetryPosition position) {
        if (position == null) {
            builder.append("null");
            return;
        }
        builder.append('{');
        field(builder, "world", position.world()).append(',');
        field(builder, "xBlock", position.blockX()).append(',');
        field(builder, "yBlock", position.blockY()).append(',');
        field(builder, "zBlock", position.blockZ()).append(',');
        field(builder, "x", position.x()).append(',');
        field(builder, "y", position.y()).append(',');
        field(builder, "z", position.z()).append(',');
        field(builder, "yaw", position.yaw()).append(',');
        field(builder, "pitch", position.pitch());
        builder.append('}');
    }

    private static void navigation(StringBuilder builder, NpcTelemetryNavigation navigation) {
        if (navigation == null) {
            builder.append("null");
            return;
        }
        builder.append('{');
        field(builder, "navigating", navigation.navigating()).append(',');
        field(builder, "targetWorld", navigation.targetWorld()).append(',');
        builder.append("\"target\":"); position(builder, navigation.target()); builder.append(',');
        field(builder, "strategy", navigation.strategy()).append(',');
        field(builder, "path", navigation.path()).append(',');
        field(builder, "examiners", navigation.examiners()).append(',');
        field(builder, "pathfinder", navigation.pathfinder()).append(',');
        field(builder, "range", navigation.range()).append(',');
        field(builder, "stationaryTicks", navigation.stationaryTicks()).append(',');
        field(builder, "distanceMargin", navigation.distanceMargin()).append(',');
        field(builder, "pathMargin", navigation.pathMargin()).append(',');
        field(builder, "cancelReason", navigation.cancelReason()).append(',');
        field(builder, "elapsedTicks", navigation.elapsedTicks());
        builder.append('}');
    }

    private static void semanticPoint(StringBuilder builder, NpcTelemetrySemanticPoint point) {
        if (point == null) {
            builder.append("null");
            return;
        }
        builder.append('{');
        field(builder, "type", point.type()).append(',');
        field(builder, "name", point.name()).append(',');
        field(builder, "world", point.world()).append(',');
        builder.append("\"position\":"); position(builder, point.position());
        builder.append('}');
    }

    private static void blockProbes(StringBuilder builder, List<NpcTelemetryBlockProbe> probes) {
        builder.append('[');
        for (int index = 0; index < probes.size(); index++) {
            if (index > 0) builder.append(',');
            blockProbe(builder, probes.get(index));
        }
        builder.append(']');
    }

    private static void blockProbe(StringBuilder builder, NpcTelemetryBlockProbe probe) {
        if (probe == null) {
            builder.append("null");
            return;
        }
        builder.append('{');
        field(builder, "relation", probe.relation()).append(',');
        field(builder, "world", probe.world()).append(',');
        field(builder, "x", probe.x()).append(',');
        field(builder, "y", probe.y()).append(',');
        field(builder, "z", probe.z()).append(',');
        field(builder, "material", probe.material()).append(',');
        field(builder, "solid", probe.solid()).append(',');
        field(builder, "passable", probe.passable()).append(',');
        field(builder, "loadedChunk", probe.loadedChunk()).append(',');
        field(builder, "door", probe.door()).append(',');
        field(builder, "fenceGate", probe.fenceGate()).append(',');
        field(builder, "fence", probe.fence()).append(',');
        field(builder, "obstacle", probe.obstacle());
        builder.append('}');
    }

    private static StringBuilder field(StringBuilder builder, String name, String value) {
        builder.append('"').append(name).append("\":");
        if (value == null) builder.append("null");
        else builder.append('"').append(escape(value)).append('"');
        return builder;
    }

    private static StringBuilder field(StringBuilder builder, String name, boolean value) {
        return builder.append('"').append(name).append("\":").append(value);
    }

    private static StringBuilder field(StringBuilder builder, String name, Boolean value) {
        builder.append('"').append(name).append("\":");
        return builder.append(value == null ? "null" : value);
    }

    private static StringBuilder field(StringBuilder builder, String name, int value) {
        return builder.append('"').append(name).append("\":").append(value);
    }

    private static StringBuilder field(StringBuilder builder, String name, long value) {
        return builder.append('"').append(name).append("\":").append(value);
    }

    private static StringBuilder field(StringBuilder builder, String name, double value) {
        builder.append('"').append(name).append("\":");
        if (Double.isFinite(value)) builder.append(String.format(Locale.ROOT, "%.4f", value));
        else builder.append("null");
        return builder;
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    else escaped.append(ch);
                }
            }
        }
        return escaped.toString();
    }
}
