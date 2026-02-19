package com.bloxbean.cardano.client.txflow.exec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmationResultTest {

    @Test
    void testSubmittedFactoryMethod() {
        ConfirmationResult result = ConfirmationResult.submitted("txHash123", 1000L);

        assertEquals("txHash123", result.getTxHash());
        assertEquals(ConfirmationStatus.SUBMITTED, result.getStatus());
        assertEquals(-1, result.getConfirmationDepth());
        assertEquals(1000L, result.getCurrentTipHeight());
        assertNull(result.getBlockHeight());
        assertNull(result.getBlockHash());
        assertNull(result.getError());
    }

    @Test
    void testRolledBackFactoryMethod() {
        Throwable error = new RuntimeException("Rollback detected");
        ConfirmationResult result = ConfirmationResult.rolledBack("txHash123", 950L, 1000L, error);

        assertEquals("txHash123", result.getTxHash());
        assertEquals(ConfirmationStatus.ROLLED_BACK, result.getStatus());
        assertEquals(-1, result.getConfirmationDepth());
        assertEquals(950L, result.getBlockHeight());
        assertEquals(1000L, result.getCurrentTipHeight());
        assertSame(error, result.getError());
    }

    @Test
    void testHasReached_SubmittedStatus() {
        ConfirmationResult result = ConfirmationResult.submitted("tx", 100L);

        assertTrue(result.hasReached(ConfirmationStatus.SUBMITTED));
        assertFalse(result.hasReached(ConfirmationStatus.IN_BLOCK));
        assertFalse(result.hasReached(ConfirmationStatus.CONFIRMED));
    }

    @Test
    void testHasReached_InBlockStatus() {
        ConfirmationResult result = ConfirmationResult.builder()
                .txHash("tx")
                .status(ConfirmationStatus.IN_BLOCK)
                .confirmationDepth(5)
                .build();

        assertTrue(result.hasReached(ConfirmationStatus.SUBMITTED));
        assertTrue(result.hasReached(ConfirmationStatus.IN_BLOCK));
        assertFalse(result.hasReached(ConfirmationStatus.CONFIRMED));
    }

    @Test
    void testHasReached_ConfirmedStatus() {
        ConfirmationResult result = ConfirmationResult.builder()
                .txHash("tx")
                .status(ConfirmationStatus.CONFIRMED)
                .confirmationDepth(50)
                .build();

        assertTrue(result.hasReached(ConfirmationStatus.SUBMITTED));
        assertTrue(result.hasReached(ConfirmationStatus.IN_BLOCK));
        assertTrue(result.hasReached(ConfirmationStatus.CONFIRMED));
    }

    @Test
    void testHasReached_RolledBackReturnsFalse() {
        ConfirmationResult result = ConfirmationResult.rolledBack("tx", 100L, 105L, null);

        // Rolled back should always return false for hasReached
        assertFalse(result.hasReached(ConfirmationStatus.SUBMITTED));
        assertFalse(result.hasReached(ConfirmationStatus.IN_BLOCK));
        assertFalse(result.hasReached(ConfirmationStatus.CONFIRMED));
    }

    @Test
    void testIsTerminal() {
        ConfirmationResult rolledBack = ConfirmationResult.rolledBack("tx", 100L, 105L, null);
        assertTrue(rolledBack.isTerminal());

        ConfirmationResult confirmed = ConfirmationResult.builder()
                .txHash("tx")
                .status(ConfirmationStatus.CONFIRMED)
                .confirmationDepth(100)
                .build();
        assertFalse(confirmed.isTerminal());

        ConfirmationResult inBlock = ConfirmationResult.builder()
                .txHash("tx")
                .status(ConfirmationStatus.IN_BLOCK)
                .confirmationDepth(5)
                .build();
        assertFalse(inBlock.isTerminal());

        ConfirmationResult submitted = ConfirmationResult.submitted("tx", 100L);
        assertFalse(submitted.isTerminal());
    }

    @Test
    void testIsRolledBack() {
        ConfirmationResult rolledBack = ConfirmationResult.rolledBack("tx", 100L, 105L, null);
        assertTrue(rolledBack.isRolledBack());

        ConfirmationResult confirmed = ConfirmationResult.builder()
                .txHash("tx")
                .status(ConfirmationStatus.CONFIRMED)
                .confirmationDepth(50)
                .build();
        assertFalse(confirmed.isRolledBack());
    }

    @Test
    void testIsHealthy() {
        ConfirmationResult submitted = ConfirmationResult.submitted("tx", 100L);
        assertTrue(submitted.isHealthy());

        ConfirmationResult confirmed = ConfirmationResult.builder()
                .txHash("tx")
                .status(ConfirmationStatus.CONFIRMED)
                .confirmationDepth(50)
                .build();
        assertTrue(confirmed.isHealthy());

        ConfirmationResult rolledBack = ConfirmationResult.rolledBack("tx", 100L, 105L, null);
        assertFalse(rolledBack.isHealthy());
    }

    @Test
    void testToStringContainsEssentialInfo() {
        ConfirmationResult result = ConfirmationResult.builder()
                .txHash("abc123")
                .status(ConfirmationStatus.CONFIRMED)
                .confirmationDepth(50)
                .blockHeight(1000L)
                .currentTipHeight(1050L)
                .build();

        String str = result.toString();
        assertTrue(str.contains("abc123"));
        assertTrue(str.contains("CONFIRMED"));
        assertTrue(str.contains("50"));
    }

    @Test
    void testToStringWithError() {
        ConfirmationResult result = ConfirmationResult.rolledBack(
                "tx", 100L, 105L, new RuntimeException("Test error message"));

        String str = result.toString();
        assertTrue(str.contains("Test error message"));
    }
}
