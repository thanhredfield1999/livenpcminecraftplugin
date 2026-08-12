package vn.heomc.livingnpc;

import java.util.Set;
import org.bukkit.Material;

record WorkZoneValidation(Set<Material> found, Set<Material> missing) {
    WorkZoneValidation {
        found = Set.copyOf(found);
        missing = Set.copyOf(missing);
    }

    boolean valid() {
        return missing.isEmpty();
    }
}
