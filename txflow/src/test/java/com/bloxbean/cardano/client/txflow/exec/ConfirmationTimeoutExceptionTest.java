package com.bloxbean.cardano.client.txflow.exec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmationTimeoutExceptionTest {

    @Test
    void testExceptionCreation() {
        String txHash = "abc123def456";
        ConfirmationTimeoutException exception = new ConfirmationTimeoutException(txHash);

        assertEquals(txHash, exception.getTransactionHash());
        assertEquals("Transaction confirmation timeout: abc123def456", exception.getMessage());
    }

    @Test
    void testExceptionInheritance() {
        ConfirmationTimeoutException exception = new ConfirmationTimeoutException("txHash123");

        // Should be a FlowExecutionException
        assertTrue(exception instanceof FlowExecutionException);

        // Should be a RuntimeException
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void testExceptionWithNullHash() {
        ConfirmationTimeoutException exception = new ConfirmationTimeoutException(null);

        assertNull(exception.getTransactionHash());
        assertEquals("Transaction confirmation timeout: null", exception.getMessage());
    }

    @Test
    void testExceptionCanBeCaught() {
        String txHash = "testTxHash";

        try {
            throw new ConfirmationTimeoutException(txHash);
        } catch (ConfirmationTimeoutException e) {
            assertEquals(txHash, e.getTransactionHash());
        }
    }

    @Test
    void testExceptionCanBeCaughtAsFlowExecutionException() {
        String txHash = "testTxHash";

        try {
            throw new ConfirmationTimeoutException(txHash);
        } catch (FlowExecutionException e) {
            assertTrue(e instanceof ConfirmationTimeoutException);
            assertEquals(txHash, ((ConfirmationTimeoutException) e).getTransactionHash());
        }
    }
}
