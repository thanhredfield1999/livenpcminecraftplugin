package vn.heomc.livingnpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServingLedgerTest {
    @Test
    void reservesAndConsumesAServingExactlyOnce() {
        ServingLedger ledger = new ServingLedger();
        UUID npc = UUID.randomUUID();
        assertTrue(ledger.publish("batch:1", 2, 0));

        ServingLedger.Reservation first = ledger.reserve(
                "meal:1", "batch:1", npc, ServingLedger.Audience.RESIDENT);
        ServingLedger.Reservation duplicate = ledger.reserve(
                "meal:1", "batch:1", npc, ServingLedger.Audience.RESIDENT);

        assertNotNull(first);
        assertSame(first, duplicate);
        assertEquals(1, ledger.available("batch:1"));
        assertEquals(ServingLedger.Status.CONSUMED, ledger.consume("meal:1").status());
        assertEquals(ServingLedger.Status.CONSUMED, ledger.consume("meal:1").status());
        assertEquals(1, ledger.available("batch:1"));
    }

    @Test
    void releaseReturnsServingAndAllowsANewReservation() {
        ServingLedger ledger = new ServingLedger();
        UUID npc = UUID.randomUUID();
        ledger.publish("batch:1", 1, 0);
        ledger.reserve("meal:1", "batch:1", npc, ServingLedger.Audience.RESIDENT);

        assertEquals(ServingLedger.Status.RELEASED, ledger.release("meal:1").status());
        assertEquals(ServingLedger.Status.RELEASED, ledger.release("meal:1").status());
        assertEquals(1, ledger.available("batch:1"));
        assertNotNull(ledger.reserve("meal:2", "batch:1", npc, ServingLedger.Audience.RESIDENT));
    }

    @Test
    void visitorQuotaCannotConsumeResidentOnlyServings() {
        ServingLedger ledger = new ServingLedger();
        ledger.publish("batch:1", 3, 1);

        assertNotNull(ledger.reserve(
                "visitor:1", "batch:1", UUID.randomUUID(), ServingLedger.Audience.VISITOR));
        assertNull(ledger.reserve(
                "visitor:2", "batch:1", UUID.randomUUID(), ServingLedger.Audience.VISITOR));
        assertNotNull(ledger.reserve(
                "resident:1", "batch:1", UUID.randomUUID(), ServingLedger.Audience.RESIDENT));
        assertEquals(1, ledger.available("batch:1"));
    }

    @Test
    void npcCannotHoldTwoActiveServings() {
        ServingLedger ledger = new ServingLedger();
        UUID npc = UUID.randomUUID();
        ledger.publish("batch:1", 2, 0);

        assertNotNull(ledger.reserve("meal:1", "batch:1", npc, ServingLedger.Audience.RESIDENT));
        assertNull(ledger.reserve("meal:2", "batch:1", npc, ServingLedger.Audience.RESIDENT));
        assertEquals(1, ledger.available("batch:1"));
    }
}
