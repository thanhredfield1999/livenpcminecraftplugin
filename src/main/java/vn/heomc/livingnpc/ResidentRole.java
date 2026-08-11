package vn.heomc.livingnpc;

import java.util.Locale;

enum ResidentRole {
    FARMER,
    FISHER,
    COOK,
    CRAFTER,
    MINER,
    RANCHER,
    SECURITY,
    MELEE_TRAINING,
    ARCHERY_TRAINING,
    SPARRING;

    String storageKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static ResidentRole parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "farm" -> FARMER;
                case "fish" -> FISHER;
                case "cook", "baker" -> COOK;
                case "craft", "blacksmith", "miller" -> CRAFTER;
                case "mine" -> MINER;
                case "ranch" -> RANCHER;
                case "guard", "sentry" -> SECURITY;
                default -> null;
            };
        }
    }
}
