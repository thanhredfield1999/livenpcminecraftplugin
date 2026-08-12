package vn.heomc.livingnpc;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

record ResidentProfile(
        String id,
        String name,
        String gender,
        String title,
        Set<ResidentRole> roles,
        String skin,
        String biography,
        List<String> personality,
        String preferredWeapon,
        List<String> goals,
        Map<UUID, ResidentRelationship> relationships) {
    ResidentProfile {
        roles = roles == null || roles.isEmpty()
                ? Set.of(ResidentRole.FARMER)
                : Set.copyOf(roles);
        biography = biography == null ? "" : biography;
        personality = personality == null
                ? List.of()
                : personality.stream().filter(Objects::nonNull).toList();
        preferredWeapon = preferredWeapon == null ? "" : preferredWeapon;
        goals = goals == null ? List.of() : goals.stream().filter(Objects::nonNull).toList();
        relationships = relationships == null ? Map.of() : Map.copyOf(relationships);
    }

    ResidentProfile(String id, String name, String gender, String title, Set<ResidentRole> roles, String skin) {
        this(id, name, gender, title, roles, skin, "", List.of(), "", List.of(), Map.of());
    }

    static ResidentProfile custom(String name) {
        return new ResidentProfile("custom", name, "unspecified", "Cư dân", Set.of(ResidentRole.FARMER), "");
    }

    String profession() {
        return roles.iterator().next().storageKey();
    }

    boolean hasRole(ResidentRole role) {
        return roles.contains(role);
    }

    ResidentRole primaryRole() {
        return roles.contains(ResidentRole.FARMER) ? ResidentRole.FARMER
                : java.util.Arrays.stream(ResidentRole.values()).filter(roles::contains).findFirst()
                        .orElse(ResidentRole.RESIDENT);
    }

    boolean hasCharacterDetails() {
        return !biography.isBlank() || !personality.isEmpty() || !preferredWeapon.isBlank()
                || !goals.isEmpty() || !relationships.isEmpty();
    }

    ResidentProfile withRole(ResidentRole role) {
        java.util.EnumSet<ResidentRole> updated = java.util.EnumSet.copyOf(roles);
        updated.add(role);
        return new ResidentProfile(
                id, name, gender, title, updated, skin,
                biography, personality, preferredWeapon, goals, relationships);
    }
}
