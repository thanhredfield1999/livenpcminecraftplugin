package vn.heomc.livingnpc;

import java.util.EnumSet;
import java.util.UUID;
import java.util.Map;

record FarmerDefinition(
        UUID npcUuid,
        String villageId,
        StoredLocation home,
        StoredLocation plot,
        int plotRadius,
        ResidentProfile profile,
        ResidentRole activeRole,
        Map<ResidentRole, RoleProgress> progress,
        Map<ResidentRole, ResidentSchedule> schedules,
        EnumSet<BehaviorFlag> behaviors) {

    FarmerDefinition {
        activeRole = activeRole == null || !profile.hasRole(activeRole)
                ? profile.primaryRole()
                : activeRole;
        progress = Map.copyOf(progress);
        schedules = Map.copyOf(schedules);
        behaviors = behaviors.clone();
    }

    FarmerDefinition(UUID npcUuid, StoredLocation home, StoredLocation plot, int plotRadius,
                     ResidentProfile profile, EnumSet<BehaviorFlag> behaviors) {
        this(npcUuid, null, home, plot, plotRadius, profile, profile.primaryRole(),
                defaultProgress(profile), Map.of(), behaviors);
    }

    FarmerDefinition(UUID npcUuid, String villageId, StoredLocation home, StoredLocation plot, int plotRadius,
                     ResidentProfile profile, EnumSet<BehaviorFlag> behaviors) {
        this(npcUuid, villageId, home, plot, plotRadius, profile, profile.primaryRole(),
                defaultProgress(profile), Map.of(), behaviors);
    }

    FarmerDefinition(
            UUID npcUuid, StoredLocation home, StoredLocation plot, int plotRadius,
            ResidentProfile profile, ResidentRole activeRole,
            Map<ResidentRole, RoleProgress> progress,
            Map<ResidentRole, ResidentSchedule> schedules,
            EnumSet<BehaviorFlag> behaviors) {
        this(npcUuid, null, home, plot, plotRadius, profile, activeRole, progress, schedules, behaviors);
    }

    boolean enabled(BehaviorFlag behavior) {
        return behaviors.contains(behavior);
    }

    @Override
    public EnumSet<BehaviorFlag> behaviors() {
        return behaviors.clone();
    }

    FarmerDefinition withBehavior(BehaviorFlag behavior, boolean enabled) {
        EnumSet<BehaviorFlag> updated = behaviors.clone();
        if (enabled) {
            updated.add(behavior);
        } else {
            updated.remove(behavior);
        }
        return new FarmerDefinition(npcUuid, villageId, home, plot, plotRadius, profile, activeRole, progress, schedules, updated);
    }

    FarmerDefinition withFarmerEnabled(boolean enabled) {
        EnumSet<BehaviorFlag> updated = behaviors.clone();
        for (BehaviorFlag behavior : EnumSet.of(
                BehaviorFlag.MASTER, BehaviorFlag.HARVEST, BehaviorFlag.PLANT)) {
            if (enabled) {
                updated.add(behavior);
            } else {
                updated.remove(behavior);
            }
        }
        return new FarmerDefinition(
                npcUuid, villageId, home, plot, plotRadius, profile, activeRole, progress, schedules, updated);
    }

    FarmerDefinition withHome(StoredLocation updatedHome) {
        return new FarmerDefinition(npcUuid, villageId, updatedHome, plot, plotRadius, profile, activeRole, progress, schedules, behaviors);
    }

    FarmerDefinition withPlot(StoredLocation updatedPlot, int updatedRadius) {
        return new FarmerDefinition(npcUuid, villageId, home, updatedPlot, updatedRadius, profile, activeRole, progress, schedules, behaviors);
    }

    FarmerDefinition withVillage(String updatedVillageId) {
        return new FarmerDefinition(npcUuid, updatedVillageId, home, plot, plotRadius, profile, activeRole, progress, schedules, behaviors);
    }

    FarmerDefinition withProfile(ResidentProfile updatedProfile) {
        return new FarmerDefinition(npcUuid, villageId, home, plot, plotRadius, updatedProfile, activeRole, progress, schedules, behaviors);
    }

    FarmerDefinition withActiveRole(ResidentRole updatedRole) {
        ResidentProfile updatedProfile = profile.withRole(updatedRole);
        java.util.EnumMap<ResidentRole, RoleProgress> updatedProgress = new java.util.EnumMap<>(ResidentRole.class);
        updatedProgress.putAll(progress);
        updatedProgress.putIfAbsent(updatedRole, new RoleProgress(0L));
        return new FarmerDefinition(
                npcUuid, villageId, home, plot, plotRadius, updatedProfile,
                updatedRole, updatedProgress, schedules, behaviors);
    }

    FarmerDefinition resetToOrdinaryResident(String residentName) {
        ResidentProfile resident = ResidentProfile.adopted(residentName);
        return new FarmerDefinition(
                npcUuid, null, home, null, 4, resident, ResidentRole.RESIDENT,
                Map.of(ResidentRole.RESIDENT, new RoleProgress(0L)),
                Map.of(), BehaviorFlag.safeDefaults());
    }

    FarmerDefinition changeProfession(String residentName, ResidentRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Nghề mới không được null");
        }
        FarmerDefinition reset = resetToOrdinaryResident(residentName);
        return role == ResidentRole.RESIDENT ? reset : reset.withActiveRole(role);
    }

    FarmerDefinition withSchedule(ResidentRole role, ResidentSchedule updated) {
        java.util.EnumMap<ResidentRole, ResidentSchedule> values = new java.util.EnumMap<>(ResidentRole.class);
        values.putAll(schedules);
        if (updated == null) {
            values.remove(role);
        } else {
            values.put(role, updated);
        }
        return new FarmerDefinition(npcUuid, villageId, home, plot, plotRadius, profile, activeRole, progress, values, behaviors);
    }

    RoleProgress progress(ResidentRole role) {
        return progress.getOrDefault(role, new RoleProgress(0L));
    }

    ResidentSchedule schedule(ResidentRole role, ResidentSchedule fallback) {
        return schedules.getOrDefault(role, fallback);
    }

    FarmerDefinition withProgress(ResidentRole role, RoleProgress updated) {
        java.util.EnumMap<ResidentRole, RoleProgress> values = new java.util.EnumMap<>(ResidentRole.class);
        values.putAll(progress);
        values.put(role, updated);
        return new FarmerDefinition(npcUuid, villageId, home, plot, plotRadius, profile, activeRole, values, schedules, behaviors);
    }

    private static Map<ResidentRole, RoleProgress> defaultProgress(ResidentProfile profile) {
        java.util.EnumMap<ResidentRole, RoleProgress> values = new java.util.EnumMap<>(ResidentRole.class);
        for (ResidentRole role : profile.roles()) {
            values.put(role, new RoleProgress(0L));
        }
        return values;
    }
}
