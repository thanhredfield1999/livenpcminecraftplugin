package vn.heomc.livingnpc;

import java.util.List;

public record NpcTelemetryEconomySnapshot(List<NpcTelemetryVillageEconomy> villages) {
    static final int MAX_VILLAGES = 16;

    public NpcTelemetryEconomySnapshot(List<NpcTelemetryVillageEconomy> villages) {
        this.villages = bounded(villages, MAX_VILLAGES);
    }

    private static <T> List<T> bounded(List<T> values, int maximum) {
        if (values == null || values.isEmpty()) return List.of();
        return List.copyOf(values.subList(0, Math.min(values.size(), maximum)));
    }
}
