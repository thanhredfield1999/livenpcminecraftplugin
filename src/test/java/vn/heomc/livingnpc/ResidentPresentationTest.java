package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResidentPresentationTest {
    @Test
    void rendersCharacterDetailsAndRelationshipDialogue() {
        ResidentProfile profile = profile();

        List<String> lines = ResidentPresentation.characterLines(profile);

        assertTrue(lines.contains("Tiểu sử: From Redfield"));
        assertTrue(lines.contains("Vũ khí ưa thích: Cung"));
        assertTrue(lines.contains("Quan hệ: Keyden_Redfield (em trai)"));
        assertEquals(
                "Chào bạn. Tôi và Keyden_Redfield đến đây từ làng Redfield.",
                ResidentPresentation.contextualDialogue(profile, FarmerPhase.WATCHING_PLAYER, "fallback"));
    }

    @Test
    void preservesFallbackForProfileWithoutRelationships() {
        ResidentProfile profile = ResidentProfile.custom("Resident");

        assertEquals("fallback", ResidentPresentation.contextualDialogue(profile, FarmerPhase.INACTIVE, "fallback"));
    }

    private ResidentProfile profile() {
        return new ResidentProfile(
                "custom", "ThanhRedfield", "male", "Người anh", Set.of(ResidentRole.FARMER), "",
                "From Redfield", List.of("Bình tĩnh"), "Cung", List.of("Giúp làng"),
                Map.of(UUID.randomUUID(), new ResidentRelationship("em trai", "Keyden_Redfield")));
    }
}
