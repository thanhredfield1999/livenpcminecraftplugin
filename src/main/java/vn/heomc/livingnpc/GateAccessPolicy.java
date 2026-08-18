package vn.heomc.livingnpc;

/** Phan quyen gate nghe; gate khong duoc mo boi role khong co quyen. */
final class GateAccessPolicy {
    private GateAccessPolicy() {
    }

    static boolean mayOpenFenceGate(ResidentRole role) {
        return role == ResidentRole.FARMER
                || role == ResidentRole.RANCHER
                || role == ResidentRole.FISHER;
    }

    static boolean mayUseConfiguredNavigationGate(FarmerDefinition definition) {
        return definition != null && mayOpenFenceGate(definition.activeRole());
    }

    static boolean mayOpenFenceGate(ResidentRole role, String gateAccessClass) {
        if (gateAccessClass == null || gateAccessClass.isBlank()) return false;
        return switch (gateAccessClass.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "SHARED" -> role != null && role != ResidentRole.RESIDENT;
            case "FARMER" -> role == ResidentRole.FARMER;
            case "RANCHER" -> role == ResidentRole.RANCHER;
            case "FISHER" -> role == ResidentRole.FISHER;
            case "CIVIL" -> role == ResidentRole.COOK
                    || role == ResidentRole.CRAFTER || role == ResidentRole.MINER;
            default -> false;
        };
    }
}
