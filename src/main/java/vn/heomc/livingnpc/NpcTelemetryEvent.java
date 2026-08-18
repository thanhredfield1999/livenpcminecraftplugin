package vn.heomc.livingnpc;

import java.util.List;
import java.util.UUID;

public record NpcTelemetryEvent(
        int schemaVersion,
        String type,
        UUID npcId,
        String name,
        String role,
        String villageId,
        String world,
        NpcTelemetryPosition npcBlock,
        NpcTelemetryPosition targetBlock,
        String state,
        String phase,
        NpcTelemetryNavigation navigation,
        String path,
        NpcTelemetryBlockProbe obstacle,
        NpcTelemetrySemanticPoint semanticPoint,
        List<NpcTelemetryBlockProbe> blockProbes,
        long timestampTick,
        long timestampMillis,
        String skinName,
        NpcTelemetryAccount account) {
    public NpcTelemetryEvent {
        blockProbes = blockProbes == null ? List.of() : List.copyOf(blockProbes);
    }

    public NpcTelemetryEvent(
            int schemaVersion, String type, UUID npcId, String name, String role, String world,
            NpcTelemetryPosition npcBlock, NpcTelemetryPosition targetBlock, String state, String phase,
            NpcTelemetryNavigation navigation, String path, NpcTelemetryBlockProbe obstacle,
            NpcTelemetrySemanticPoint semanticPoint, List<NpcTelemetryBlockProbe> blockProbes,
            long timestampTick, long timestampMillis, String skinName, NpcTelemetryAccount account) {
        this(schemaVersion, type, npcId, name, role, null, world, npcBlock, targetBlock, state, phase, navigation,
                path, obstacle, semanticPoint, blockProbes, timestampTick, timestampMillis, skinName, account);
    }

    public NpcTelemetryEvent(
            int schemaVersion, String type, UUID npcId, String name, String role, String world,
            NpcTelemetryPosition npcBlock, NpcTelemetryPosition targetBlock, String state, String phase,
            NpcTelemetryNavigation navigation, String path, NpcTelemetryBlockProbe obstacle,
            NpcTelemetrySemanticPoint semanticPoint, List<NpcTelemetryBlockProbe> blockProbes,
            long timestampTick, long timestampMillis, String skinName) {
        this(schemaVersion, type, npcId, name, role, null, world, npcBlock, targetBlock, state, phase, navigation,
                path, obstacle, semanticPoint, blockProbes, timestampTick, timestampMillis, skinName, null);
    }

    public NpcTelemetryEvent(
            int schemaVersion, String type, UUID npcId, String name, String role, String world,
            NpcTelemetryPosition npcBlock, NpcTelemetryPosition targetBlock, String state, String phase,
            NpcTelemetryNavigation navigation, String path, NpcTelemetryBlockProbe obstacle,
            NpcTelemetrySemanticPoint semanticPoint, List<NpcTelemetryBlockProbe> blockProbes,
            long timestampTick, long timestampMillis) {
        this(schemaVersion, type, npcId, name, role, null, world, npcBlock, targetBlock, state, phase, navigation,
                path, obstacle, semanticPoint, blockProbes, timestampTick, timestampMillis, null, null);
    }

    public NpcTelemetryPosition npcPrecise() {
        return npcBlock;
    }

    public NpcTelemetryPosition targetPrecise() {
        return targetBlock;
    }
}
