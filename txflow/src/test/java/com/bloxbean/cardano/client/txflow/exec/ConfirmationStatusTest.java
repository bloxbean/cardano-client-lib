package com.bloxbean.cardano.client.txflow.exec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmationStatusTest {

    @Test
    void testStatusOrdering() {
        // Verify the expected ordering of statuses (excluding ROLLED_BACK)
        assertTrue(ConfirmationStatus.SUBMITTED.ordinal() < ConfirmationStatus.IN_BLOCK.ordinal());
        assertTrue(ConfirmationStatus.IN_BLOCK.ordinal() < ConfirmationStatus.CONFIRMED.ordinal());
        assertTrue(ConfirmationStatus.CONFIRMED.ordinal() < ConfirmationStatus.FINALIZED.ordinal());
    }

    @Test
    void testAllStatusesExist() {
        // Verify all expected statuses are defined
        assertEquals(5, ConfirmationStatus.values().length);
        assertNotNull(ConfirmationStatus.SUBMITTED);
        assertNotNull(ConfirmationStatus.IN_BLOCK);
        assertNotNull(ConfirmationStatus.CONFIRMED);
        assertNotNull(ConfirmationStatus.FINALIZED);
        assertNotNull(ConfirmationStatus.ROLLED_BACK);
    }

    @Test
    void testValueOfFromString() {
        assertEquals(ConfirmationStatus.SUBMITTED, ConfirmationStatus.valueOf("SUBMITTED"));
        assertEquals(ConfirmationStatus.IN_BLOCK, ConfirmationStatus.valueOf("IN_BLOCK"));
        assertEquals(ConfirmationStatus.CONFIRMED, ConfirmationStatus.valueOf("CONFIRMED"));
        assertEquals(ConfirmationStatus.FINALIZED, ConfirmationStatus.valueOf("FINALIZED"));
        assertEquals(ConfirmationStatus.ROLLED_BACK, ConfirmationStatus.valueOf("ROLLED_BACK"));
    }
}
