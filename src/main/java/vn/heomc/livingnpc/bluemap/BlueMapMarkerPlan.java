package vn.heomc.livingnpc.bluemap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record BlueMapMarkerPlan(Map<String, BlueMapMarkerSpec> markers, Set<String> staleMarkerIds) {
    public BlueMapMarkerPlan {
        markers = Map.copyOf(new LinkedHashMap<>(markers == null ? Map.of() : markers));
        staleMarkerIds = Set.copyOf(staleMarkerIds == null ? Set.of() : staleMarkerIds);
    }
}
