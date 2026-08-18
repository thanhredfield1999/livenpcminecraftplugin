package vn.heomc.livingnpc.bluemap;

import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.PluginManager;
import vn.heomc.livingnpc.NpcTelemetrySnapshot;

public final class BlueMapMarkerService {
    private final PluginManager pluginManager;
    private final Logger logger;
    private final Set<String> knownMarkerIds = new HashSet<>();
    private boolean readyLogged;
    private boolean missingLogged;

    public BlueMapMarkerService(PluginManager pluginManager, Logger logger) {
        this.pluginManager = pluginManager;
        this.logger = logger;
    }

    public void update(NpcTelemetrySnapshot snapshot, long serverTick, BlueMapSettings settings) {
        if (settings == null || !settings.enabled()) return;
        Optional<BlueMapAPI> api = currentApi();
        if (api.isEmpty()) return;
        BlueMapMarkerPlan plan = BlueMapMarkerPlanner.plan(snapshot, serverTick, settings.staleTicks());
        if (snapshot.economy() != null) {
            long withCenter = snapshot.economy().villages().stream()
                    .filter(village -> village != null && village.center() != null).count();
            logger.info("LivingNPC BlueMap economy snapshot villages=" + snapshot.economy().villages().size()
                    + " withCenter=" + withCenter + " plannedEconomy=" + plan.markers().keySet().stream()
                    .filter(id -> id.startsWith("livingnpc-economy-")).count());
        }
        apply(api.get(), plan);
    }

    public void clear() {
        Optional<BlueMapAPI> api = BlueMapAPI.getInstance();
        if (api.isEmpty()) {
            knownMarkerIds.clear();
            return;
        }
        for (BlueMapMap map : api.get().getMaps()) {
            MarkerSet set = map.getMarkerSets().get(BlueMapMarkerPlanner.MARKER_SET_ID);
            if (set != null) {
                for (String id : Set.copyOf(knownMarkerIds)) set.remove(id);
            }
        }
        knownMarkerIds.clear();
    }

    private Optional<BlueMapAPI> currentApi() {
        if (pluginManager.getPlugin("BlueMap") == null || !pluginManager.isPluginEnabled("BlueMap")) {
            if (!missingLogged) {
                logger.info("LivingNPC BlueMap markers enabled, but BlueMap plugin is missing or disabled; markers fail-closed.");
                missingLogged = true;
            }
            return Optional.empty();
        }
        Optional<BlueMapAPI> api = BlueMapAPI.getInstance();
        if (api.isEmpty()) {
            if (!missingLogged) {
                logger.info("LivingNPC BlueMap markers waiting for BlueMap API readiness.");
                missingLogged = true;
            }
            return Optional.empty();
        }
        if (!readyLogged) {
            logger.info("LivingNPC BlueMap markers connected to BlueMap " + api.get().getBlueMapVersion()
                    + " / API " + api.get().getAPIVersion() + ".");
            readyLogged = true;
        }
        return api;
    }

    private void apply(BlueMapAPI api, BlueMapMarkerPlan plan) {
        Set<String> activeThisUpdate = new HashSet<>();
        int mapCount = api.getMaps().size();
        int matchedMapCount = 0;
        for (Map.Entry<String, BlueMapMarkerSpec> entry : plan.markers().entrySet()) {
            BlueMapMarkerSpec spec = entry.getValue();
            int applied = 0;
            for (BlueMapMap map : api.getMaps()) {
                if (!matchesWorld(map, spec.world())) continue;
                matchedMapCount++;
                try {
                    markerSet(map).put(spec.id(), marker(spec));
                    applied++;
                } catch (RuntimeException exception) {
                    logger.log(Level.FINE, "Could not apply BlueMap marker " + spec.id(), exception);
                }
            }
            if (applied > 0) activeThisUpdate.add(spec.id());
        }
        Set<String> remove = new HashSet<>(knownMarkerIds);
        remove.removeAll(activeThisUpdate);
        remove.addAll(plan.staleMarkerIds());
        removeMarkers(api, remove);
        knownMarkerIds.clear();
        knownMarkerIds.addAll(activeThisUpdate);
        logger.info("LivingNPC BlueMap markers applied=" + activeThisUpdate.size()
                + " planned=" + plan.markers().size() + " maps=" + mapCount
                + " matched=" + matchedMapCount + ".");
    }

    private static MarkerSet markerSet(BlueMapMap map) {
        return map.getMarkerSets().computeIfAbsent(BlueMapMarkerPlanner.MARKER_SET_ID,
                ignored -> new MarkerSet(BlueMapMarkerPlanner.MARKER_SET_LABEL, true, false));
    }

    private static de.bluecolored.bluemap.api.markers.Marker marker(BlueMapMarkerSpec spec) {
        BlueMapMarkerPosition position = spec.position();
        if (spec.routeTarget() != null) {
            BlueMapMarkerPosition target = spec.routeTarget();
            LineMarker marker = new LineMarker(spec.label(), new Line(
                    new Vector3d(position.x(), position.y(), position.z()),
                    new Vector3d(target.x(), target.y(), target.z())));
            marker.setDetail(spec.detail());
            marker.setLineColor(new Color(spec.routeColor()));
            marker.setLineWidth(3);
            return marker;
        }
        POIMarker marker = new POIMarker(spec.label(), new Vector3d(position.x(), position.y(), position.z()));
        marker.setDetail(spec.detail());
        marker.setListed(true);
        marker.setSorting(spec.semantic() ? 100 : 0);
        if (spec.iconUrl() != null && spec.iconWidth() > 0 && spec.iconHeight() > 0) {
            marker.setIcon(spec.iconUrl(), spec.iconWidth(), spec.iconHeight());
            marker.setAnchor(new Vector2i(spec.iconWidth() / 2, spec.iconHeight()));
        }
        return marker;
    }

    private void removeMarkers(BlueMapAPI api, Set<String> markerIds) {
        if (markerIds.isEmpty()) return;
        for (BlueMapMap map : api.getMaps()) {
            MarkerSet set = map.getMarkerSets().get(BlueMapMarkerPlanner.MARKER_SET_ID);
            if (set == null) continue;
            for (String markerId : markerIds) {
                try {
                    set.remove(markerId);
                } catch (RuntimeException exception) {
                    logger.log(Level.FINE, "Could not remove BlueMap marker " + markerId, exception);
                }
            }
        }
    }

    private static boolean matchesWorld(BlueMapMap map, String world) {
        if (map == null || world == null || world.isBlank()) return false;
        String expected = normalizeWorld(world);
        String mapId = normalizeWorld(map.getId());
        String mapWorld = map.getWorld() == null ? null : normalizeWorld(map.getWorld().getId());
        return expected.equals(mapId) || expected.equals(mapWorld);
    }

    private static String normalizeWorld(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        int separator = normalized.indexOf(':');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }
}
