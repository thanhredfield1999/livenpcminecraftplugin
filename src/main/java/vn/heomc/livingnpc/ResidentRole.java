package vn.heomc.livingnpc;

import java.util.Locale;

enum ResidentRole {
    RESIDENT,
    VISITOR,
    FARMER,
    FISHER,
    COOK,
    CRAFTER,
    MINER,
    RANCHER,
    MERCHANT,
    SECURITY,
    MELEE_TRAINING,
    ARCHERY_TRAINING,
    SPARRING;

    String storageKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    boolean usesFarmerSetup() {
        return this == FARMER;
    }

    boolean implemented() {
        return this == RESIDENT || this == FARMER || this == FISHER || this == RANCHER
                || this == COOK || this == CRAFTER || this == MINER || this == MERCHANT || this == SECURITY;
    }

    static ResidentRole parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "villager", "citizen", "worker" -> RESIDENT;
                case "guest", "traveler", "traveller" -> VISITOR;
                case "farm" -> FARMER;
                case "fish" -> FISHER;
                case "cook", "baker" -> COOK;
                case "craft", "blacksmith", "miller" -> CRAFTER;
                case "mine" -> MINER;
                case "ranch" -> RANCHER;
                case "merchant", "trader", "shopkeeper" -> MERCHANT;
                case "guard", "sentry" -> SECURITY;
                default -> null;
            };
        }
    }
}
