package vn.heomc.livingnpc;

import java.util.ArrayList;
import java.util.List;

final class ResidentPresentation {
    private ResidentPresentation() {
    }

    static List<String> characterLines(ResidentProfile profile) {
        List<String> lines = new ArrayList<>();
        if (!profile.biography().isBlank()) {
            lines.add("Tiểu sử: " + profile.biography());
        }
        if (!profile.personality().isEmpty()) {
            lines.add("Tính cách: " + String.join(", ", profile.personality()));
        }
        if (!profile.preferredWeapon().isBlank()) {
            lines.add("Vũ khí ưa thích: " + profile.preferredWeapon());
        }
        if (!profile.goals().isEmpty()) {
            lines.add("Mục tiêu: " + profile.goals().getFirst());
        }
        if (!profile.relationships().isEmpty()) {
            ResidentRelationship relationship = profile.relationships().values().iterator().next();
            String name = relationship.name().isBlank() ? "một cư dân khác" : relationship.name();
            String type = relationship.type().isBlank() ? "người thân" : relationship.type();
            lines.add("Quan hệ: " + name + " (" + type + ")");
        }
        return List.copyOf(lines);
    }

    static String contextualDialogue(ResidentProfile profile, FarmerPhase phase, String fallback) {
        if (profile.relationships().isEmpty()) {
            return fallback;
        }
        ResidentRelationship relationship = profile.relationships().values().iterator().next();
        String name = relationship.name().isBlank() ? "người thân của tôi" : relationship.name();
        return switch (phase == null ? FarmerPhase.INACTIVE : phase) {
            case SOCIALIZING -> "Tôi đang trò chuyện một chút, nhưng vẫn để ý xem " + name + " đang ở đâu.";
            case WATCHING_PLAYER -> "Chào bạn. Tôi và " + name + " đến đây từ làng Redfield.";
            case GOING_TO_MARKET, SHOPPING -> "Tôi đang xem có món gì hữu ích cho tôi và " + name + ".";
            case INACTIVE -> profile.goals().isEmpty()
                    ? "Tôi đang đợi " + name + " để cùng đi."
                    : "Tôi đang đợi " + name + ". Mục tiêu của chúng tôi là "
                            + lowerFirst(profile.goals().getFirst()) + ".";
            default -> fallback;
        };
    }

    private static String lowerFirst(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }
}
