package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MealRequestBoardTest {
    @Test
    void aggregatesDemandAndCapsBatchesAtStockTarget() {
        MealRequestBoard board = new MealRequestBoard();

        MealRequest request = board.update("village", recipe("bread", 2, 10, 60), 7, 8, 8, 100L);

        assertEquals(2, request.batches());
        assertEquals(1, board.requests("village").size());
        assertEquals(7, board.requests("village").getFirst().waitingResidents());
    }

    @Test
    void claimIsExclusiveAndPrioritizesRecipe() {
        MealRequestBoard board = new MealRequestBoard();
        board.update("village", recipe("bread", 1, 16, 70), 2, 0, 4, 100L);
        board.update("village", recipe("cod", 1, 16, 50), 8, 0, 4, 100L);
        UUID firstCook = UUID.randomUUID();

        MealRequest first = board.claimBest("village", firstCook, request -> true);
        MealRequest second = board.claimBest("village", UUID.randomUUID(), request -> true);

        assertEquals("bread", first.recipeId());
        assertEquals("cod", second.recipeId());
        assertNull(board.claimBest("other-village", UUID.randomUUID(), request -> true));
    }

    @Test
    void doesNotCreateRequestWhenTargetIsFull() {
        MealRequestBoard board = new MealRequestBoard();

        assertNull(board.update("village", recipe("bread", 1, 8, 60), 4, 8, 4, 100L));
        assertEquals(0, board.requests("village").size());
    }

    private ProductionRecipe recipe(String id, int servings, int target, int priority) {
        return new ProductionRecipe(
                id, ResidentRole.COOK, Map.of("wheat", 1), id, 1, target, "Nau",
                KitchenApplianceType.FURNACE, Map.of("coal", 1), 100L,
                servings, 30, 0, priority, "wooden_shovel");
    }
}
