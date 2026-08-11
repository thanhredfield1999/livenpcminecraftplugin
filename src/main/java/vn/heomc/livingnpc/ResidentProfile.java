package vn.heomc.livingnpc;

import java.util.EnumSet;
import java.util.Set;

record ResidentProfile(String id, String name, String gender, String title, Set<ResidentRole> roles, String skin) {
    ResidentProfile {
        roles = roles == null || roles.isEmpty()
                ? Set.of(ResidentRole.FARMER)
                : Set.copyOf(roles);
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
        return roles.contains(ResidentRole.FARMER) ? ResidentRole.FARMER : roles.iterator().next();
    }
}
