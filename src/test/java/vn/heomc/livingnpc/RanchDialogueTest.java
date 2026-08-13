package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class RanchDialogueTest {
    @Test
    void talksAboutBreedingCowsWhenPairAndFoodAreReady() {
        for (int attempt = 0; attempt < 30; attempt++) {
            String line = RanchDialogue.line(new RanchDialogue.Context(
                    RanchDialogue.Species.COW, 2, 0, 2, 0, 12)).toLowerCase(java.util.Locale.ROOT);

            assertTrue(line.contains("bò"));
            assertTrue(line.contains("trưởng thành") || line.contains("sinh sản"));
            assertFalse(line.contains("khỏe"));
        }
    }

    @Test
    void noticesBabyChickensBeforeRoutineWork() {
        String line = RanchDialogue.line(new RanchDialogue.Context(
                RanchDialogue.Species.CHICKEN, 4, 2, 2, 0, 20)).toLowerCase(java.util.Locale.ROOT);

        assertTrue(line.contains("gà con") || line.contains("con non"));
    }

    @Test
    void explainsMissingCompanionForSingleAnimal() {
        String line = RanchDialogue.line(new RanchDialogue.Context(
                RanchDialogue.Species.PIG, 1, 0, 1, 0, 10)).toLowerCase(java.util.Locale.ROOT);

        assertTrue(line.contains("một") || line.contains("thêm một"));
        assertTrue(line.contains("lợn"));
    }
}
