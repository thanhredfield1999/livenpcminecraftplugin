package vn.heomc.livingnpc.bluemap;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import vn.heomc.livingnpc.NpcTelemetryAccount;
import vn.heomc.livingnpc.NpcTelemetryActivity;
import vn.heomc.livingnpc.NpcTelemetryEconomySnapshot;
import vn.heomc.livingnpc.NpcTelemetryEvent;
import vn.heomc.livingnpc.NpcTelemetryGate;
import vn.heomc.livingnpc.NpcTelemetryInventoryItem;
import vn.heomc.livingnpc.NpcTelemetryNavigation;
import vn.heomc.livingnpc.NpcTelemetryPosition;
import vn.heomc.livingnpc.NpcTelemetryRoleProduction;
import vn.heomc.livingnpc.NpcTelemetrySemanticPoint;
import vn.heomc.livingnpc.NpcTelemetrySnapshot;
import vn.heomc.livingnpc.NpcTelemetryVillageEconomy;

public final class BlueMapMarkerPlanner {
    public static final String MARKER_SET_ID = "livingnpc-observatory";
    static final String MARKER_SET_LABEL = "LivingNPC Observatory";
    private static final String HEADS_PREFIX = "https://mc-heads.net/avatar/";
    private static final String FALLBACK_SKIN = "MHF_Steve";

    private BlueMapMarkerPlanner() {
    }

    public static BlueMapMarkerPlan plan(NpcTelemetrySnapshot snapshot, long currentTick, long staleTicks) {
        if (snapshot == null) return new BlueMapMarkerPlan(Map.of(), Set.of());
        long boundedStaleTicks = Math.max(20L, staleTicks);
        Map<UUID, NpcTelemetryEvent> latest = new LinkedHashMap<>();
        for (NpcTelemetryEvent event : snapshot.events()) {
            if (event == null || event.npcId() == null) continue;
            NpcTelemetryEvent previous = latest.get(event.npcId());
            if (previous == null || event.timestampTick() >= previous.timestampTick()) latest.put(event.npcId(), event);
        }

        Map<String, BlueMapMarkerSpec> markers = new LinkedHashMap<>();
        Set<String> stale = new LinkedHashSet<>();
        latest.values().stream()
                .sorted(Comparator.comparing(event -> safe(event.name())))
                .forEach(event -> addEvent(markers, stale, event, currentTick, boundedStaleTicks));
        snapshot.gates().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(NpcTelemetryGate::id))
                .forEach(gate -> addGate(markers, gate));
        addEconomy(markers, snapshot.economy(), snapshot.events(), currentTick, boundedStaleTicks);
        return new BlueMapMarkerPlan(markers, stale);
    }

    private static void addEconomy(Map<String, BlueMapMarkerSpec> markers, NpcTelemetryEconomySnapshot economy,
            List<NpcTelemetryEvent> events, long currentTick, long staleTicks) {
        if (economy == null) return;
        Map<String, NpcTelemetryEvent> latest = new LinkedHashMap<>();
        for (NpcTelemetryEvent event : events) {
            if (event != null && !blank(event.villageId()) && currentTick - event.timestampTick() <= staleTicks) {
                latest.put(event.villageId(), event);
            }
        }
        economy.villages().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(village -> safe(village.villageId())))
                .forEach(village -> addEconomyVillage(markers, village, latest.get(village.villageId())));
    }

    private static void addEconomyVillage(Map<String, BlueMapMarkerSpec> markers, NpcTelemetryVillageEconomy village,
            NpcTelemetryEvent fallback) {
        NpcTelemetryPosition position = valid(village.center()) ? village.center()
                : fallback == null ? null : fallback.npcPrecise();
        if (blank(village.villageId()) || !valid(position)) return;
        String id = economyMarkerId(village.villageId());
        String villageId = html(village.villageId());
        String inventory = inventory(village.inventory());
        String production = production(village.roleProduction());
        String activities = activities(village.activities());
        String label = "Village economy " + villageId + " | balance=" + village.balanceMinor() + ' '
                + html(village.currencyUnit());
        String detail = "kind=VILLAGE_ECONOMY"
                + "<br>villageId=" + villageId
                + "<br>balanceMinor=" + village.balanceMinor()
                + "<br>totalEarnedMinor=" + village.totalEarnedMinor()
                + "<br>totalSpentMinor=" + village.totalSpentMinor()
                + "<br>currencyUnit=" + html(village.currencyUnit())
                + "<br>inventoryTotal=" + inventoryTotal(village.inventory())
                + "<br>inventory=" + inventory
                + "<br>roleProduction=" + production
                + "<br>activities=" + activities
                + "<br>timestampTick=" + (village.center() == null && fallback != null ? fallback.timestampTick() : "live");
        markers.put(id, new BlueMapMarkerSpec(
                id,
                position.world(),
                new BlueMapMarkerPosition(position.x(), position.y(), position.z()),
                label,
                detail,
                true,
                null));
    }

    private static long inventoryTotal(List<NpcTelemetryInventoryItem> inventory) {
        return inventory.stream().limit(32).filter(Objects::nonNull).mapToLong(NpcTelemetryInventoryItem::amount).sum();
    }

    private static String inventory(List<NpcTelemetryInventoryItem> inventory) {
        return inventory.stream().limit(32).filter(Objects::nonNull)
                .map(item -> html(item.item()) + ':' + item.amount()).reduce((left, right) -> left + ',' + right).orElse("");
    }

    private static String production(List<NpcTelemetryRoleProduction> production) {
        return production.stream().limit(32).filter(Objects::nonNull)
                .map(value -> html(value.role()) + ':' + value.amount()).reduce((left, right) -> left + ',' + right).orElse("");
    }

    private static String activities(List<NpcTelemetryActivity> activities) {
        return activities.stream().limit(10).filter(Objects::nonNull)
                .map(activity -> html(activity.role()) + ':' + html(activity.action()) + ':'
                        + html(activity.item()) + ':' + activity.amount() + ':'
                        + (activity.createdAt() == null ? "" : activity.createdAt().toEpochMilli()))
                .reduce((left, right) -> left + ',' + right).orElse("");
    }

    private static void addGate(Map<String, BlueMapMarkerSpec> markers, NpcTelemetryGate gate) {
        if (blank(gate.id()) || blank(gate.world())) return;
        String id = gateMarkerId(gate.id());
        String status = safe(gate.status());
        String label = "Gate " + html(gate.id()) + " | status=" + html(status);
        String detail = "id=" + html(gate.id())
                + "<br>coords=" + html(gate.world()) + ':' + gate.x() + ',' + gate.y() + ',' + gate.z()
                + "<br>material=" + html(gate.material())
                + "<br>status=" + html(status)
                + "<br>open=" + (gate.open() == null ? "UNKNOWN" : gate.open())
                + "<br>action=" + html(gate.action())
                + "<br>timestampTick=" + gate.timestampTick();
        markers.put(id, new BlueMapMarkerSpec(
                id,
                gate.world(),
                new BlueMapMarkerPosition(gate.x() + 0.5, gate.y(), gate.z() + 0.5),
                label,
                detail,
                true,
                null));
    }

    private static void addEvent(
            Map<String, BlueMapMarkerSpec> markers, Set<String> stale,
            NpcTelemetryEvent event, long currentTick, long staleTicks) {
        String npcId = npcMarkerId(event.npcId());
        NpcTelemetryPosition npcPosition = event.npcPrecise();
        if (!valid(npcPosition) || currentTick - event.timestampTick() > staleTicks) {
            stale.add(npcId);
            stale.add(routeMarkerId(event.npcId()));
            addSemanticStale(stale, event);
            return;
        }
        String label = npcLabel(event);
        String diagnosis = diagnosis(event);
        markers.put(npcId, new BlueMapMarkerSpec(
                npcId,
                npcPosition.world(),
                new BlueMapMarkerPosition(npcPosition.x(), npcPosition.y(), npcPosition.z()),
                label,
                detail(event, diagnosis),
                false,
                null,
                skinIconUrl(event.skinName()),
                64,
                64,
                null,
                null));
        addRoute(markers, stale, event, npcPosition, label, diagnosis);
        addSemantic(markers, event);
    }

    private static void addRoute(
            Map<String, BlueMapMarkerSpec> markers, Set<String> stale, NpcTelemetryEvent event,
            NpcTelemetryPosition npcPosition, String npcLabel, String diagnosis) {
        String routeId = routeMarkerId(event.npcId());
        NpcTelemetryPosition target = event.targetPrecise();
        if (!valid(target) || !npcPosition.world().equalsIgnoreCase(target.world())) {
            stale.add(routeId);
            return;
        }
        markers.put(routeId, new BlueMapMarkerSpec(
                routeId,
                npcPosition.world(),
                new BlueMapMarkerPosition(npcPosition.x(), npcPosition.y(), npcPosition.z()),
                npcLabel + " | " + html(diagnosis),
                detail(event, diagnosis),
                false,
                null,
                null,
                0,
                0,
                new BlueMapMarkerPosition(target.x(), target.y(), target.z()),
                routeColor(event)));
    }

    private static void addSemantic(Map<String, BlueMapMarkerSpec> markers, NpcTelemetryEvent event) {
        NpcTelemetrySemanticPoint point = event.semanticPoint();
        if (point == null || point.position() == null || blank(point.position().world())) return;
        String id = semanticMarkerId(event.npcId(), point);
        String label = html(point.name());
        markers.put(id, new BlueMapMarkerSpec(
                id,
                point.position().world(),
                new BlueMapMarkerPosition(point.position().x(), point.position().y(), point.position().z()),
                label,
                html(point.type()) + ": " + label,
                true,
                null));
    }

    private static void addSemanticStale(Set<String> stale, NpcTelemetryEvent event) {
        if (event.semanticPoint() == null || event.npcId() == null) return;
        stale.add(semanticMarkerId(event.npcId(), event.semanticPoint()));
    }

    private static String detail(NpcTelemetryEvent event, String diagnosis) {
        NpcTelemetryNavigation navigation = event.navigation();
        NpcTelemetryPosition target = event.targetPrecise();
        String obstacle = event.obstacle() == null ? "none"
                : safe(event.obstacle().relation()) + ":" + safe(event.obstacle().material());
        return "name=" + html(event.name())
                + "<br>role=" + html(event.role())
                + "<br>job=" + html(event.role())
                + "<br>state=" + html(event.state())
                + "<br>phase=" + html(event.phase())
                + "<br>path=" + html(pathState(event))
                + "<br>cancelReason=" + html(navigation == null ? "none" : navigation.cancelReason())
                + "<br>target=" + html(position(target))
                + "<br>obstacle/blocker=" + html(obstacle)
                + "<br>action=" + html(diagnosis)
                + accountDetail(event.account());
    }

    private static String accountDetail(NpcTelemetryAccount account) {
        if (account == null) return "<br>account=unavailable";
        StringBuilder detail = new StringBuilder("<br>money=")
                .append(account.balanceMinor()).append(' ').append(html(account.currencyUnit()))
                .append("<br>inventoryTotal=").append(account.inventoryTotal());
        account.inventory().stream().limit(8).forEach(item -> detail.append("<br>")
                .append(html(item.item())).append('=').append(item.amount()));
        return detail.toString();
    }

    private static String diagnosis(NpcTelemetryEvent event) {
        NpcTelemetryNavigation navigation = event.navigation();
        if (stuck(event)) return "Kẹt khi điều hướng";
        if (event.targetPrecise() == null) return "Chưa có đích điều hướng";
        if (navigation != null && navigation.navigating() && "absent".equals(pathState(event))) return "Đang tìm đường";
        if ("present".equals(pathState(event))) return "Đang đi theo đường";
        return "Đang chờ telemetry điều hướng";
    }

    private static String routeColor(NpcTelemetryEvent event) {
        if (stuck(event)) return "#ef4444";
        NpcTelemetryNavigation navigation = event.navigation();
        if (navigation != null && navigation.navigating() && "absent".equals(pathState(event))) return "#f59e0b";
        if ("present".equals(pathState(event))) return "#22c55e";
        return "#64748b";
    }

    private static boolean stuck(NpcTelemetryEvent event) {
        return "STUCK".equalsIgnoreCase(safe(event.state()))
                || event.navigation() != null && "STUCK".equalsIgnoreCase(safe(event.navigation().cancelReason()));
    }

    private static String pathState(NpcTelemetryEvent event) {
        if (event.navigation() != null && !blank(event.navigation().path())) return event.navigation().path();
        return safe(event.path());
    }

    private static String position(NpcTelemetryPosition position) {
        if (!valid(position)) return "none";
        return position.world() + ":" + position.x() + "," + position.y() + "," + position.z();
    }

    private static String skinIconUrl(String skinName) {
        String safeSkin = skinName != null && skinName.matches("[A-Za-z0-9_]{1,16}") ? skinName : FALLBACK_SKIN;
        return HEADS_PREFIX + safeSkin + "/64.png";
    }

    private static String npcLabel(NpcTelemetryEvent event) {
        return "NPC " + html(event.name())
                + " | job=" + html(event.role())
                + " | state=" + html(event.state())
                + " | phase=" + html(event.phase());
    }

    static String npcMarkerId(UUID npcId) {
        return "livingnpc-npc-" + npcId;
    }

    static String routeMarkerId(UUID npcId) {
        return "livingnpc-route-" + npcId;
    }

    static String gateMarkerId(String gateId) {
        return "livingnpc-gate-" + gateId;
    }

    static String economyMarkerId(String villageId) {
        return "livingnpc-economy-" + slug(villageId);
    }

    static String semanticMarkerId(UUID npcId, NpcTelemetrySemanticPoint point) {
        String suffix = slug(safe(point.type()) + "-" + safe(point.name()));
        return "livingnpc-semantic-" + npcId + "-" + suffix;
    }

    static String html(String value) {
        return safe(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String slug(String value) {
        String slug = safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "point" : slug;
    }

    private static String safe(String value) {
        return Objects.toString(value, "");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean valid(NpcTelemetryPosition position) {
        return position != null && !blank(position.world())
                && Double.isFinite(position.x()) && Double.isFinite(position.y()) && Double.isFinite(position.z());
    }
}
