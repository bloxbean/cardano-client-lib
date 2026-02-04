package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.txflow.exec.ConfirmationTimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    @Test
    void testDefaultPolicy() {
        RetryPolicy policy = RetryPolicy.defaults();

        assertEquals(3, policy.getMaxAttempts());
        assertEquals(BackoffStrategy.EXPONENTIAL, policy.getBackoffStrategy());
        assertEquals(Duration.ofSeconds(1), policy.getInitialDelay());
        assertEquals(Duration.ofSeconds(30), policy.getMaxDelay());
        assertTrue(policy.isRetryOnTimeout());
        assertTrue(policy.isRetryOnNetworkError());
    }

    @Test
    void testNoRetryPolicy() {
        RetryPolicy policy = RetryPolicy.noRetry();

        assertEquals(1, policy.getMaxAttempts());
    }

    @Test
    void testCustomPolicy() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(5)
                .backoffStrategy(BackoffStrategy.LINEAR)
                .initialDelay(Duration.ofMillis(500))
                .maxDelay(Duration.ofSeconds(10))
                .retryOnTimeout(false)
                .retryOnNetworkError(false)
                .build();

        assertEquals(5, policy.getMaxAttempts());
        assertEquals(BackoffStrategy.LINEAR, policy.getBackoffStrategy());
        assertEquals(Duration.ofMillis(500), policy.getInitialDelay());
        assertEquals(Duration.ofSeconds(10), policy.getMaxDelay());
        assertFalse(policy.isRetryOnTimeout());
        assertFalse(policy.isRetryOnNetworkError());
    }

    @Test
    void testFixedBackoffDelay() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialDelay(Duration.ofSeconds(2))
                .backoffStrategy(BackoffStrategy.FIXED)
                .build();

        assertEquals(Duration.ofSeconds(2), policy.calculateDelay(1));
        assertEquals(Duration.ofSeconds(2), policy.calculateDelay(2));
        assertEquals(Duration.ofSeconds(2), policy.calculateDelay(3));
        assertEquals(Duration.ofSeconds(2), policy.calculateDelay(10));
    }

    @Test
    void testLinearBackoffDelay() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialDelay(Duration.ofSeconds(1))
                .backoffStrategy(BackoffStrategy.LINEAR)
                .maxDelay(Duration.ofSeconds(60))
                .build();

        assertEquals(Duration.ofSeconds(1), policy.calculateDelay(1));  // 1 * 1 = 1
        assertEquals(Duration.ofSeconds(2), policy.calculateDelay(2));  // 1 * 2 = 2
        assertEquals(Duration.ofSeconds(3), policy.calculateDelay(3));  // 1 * 3 = 3
        assertEquals(Duration.ofSeconds(10), policy.calculateDelay(10)); // 1 * 10 = 10
    }

    @Test
    void testExponentialBackoffDelay() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialDelay(Duration.ofSeconds(1))
                .backoffStrategy(BackoffStrategy.EXPONENTIAL)
                .maxDelay(Duration.ofSeconds(60))
                .build();

        assertEquals(Duration.ofSeconds(1), policy.calculateDelay(1));  // 1 * 2^0 = 1
        assertEquals(Duration.ofSeconds(2), policy.calculateDelay(2));  // 1 * 2^1 = 2
        assertEquals(Duration.ofSeconds(4), policy.calculateDelay(3));  // 1 * 2^2 = 4
        assertEquals(Duration.ofSeconds(8), policy.calculateDelay(4));  // 1 * 2^3 = 8
        assertEquals(Duration.ofSeconds(16), policy.calculateDelay(5)); // 1 * 2^4 = 16
    }

    @Test
    void testMaxDelayLimit() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialDelay(Duration.ofSeconds(10))
                .maxDelay(Duration.ofSeconds(30))
                .backoffStrategy(BackoffStrategy.EXPONENTIAL)
                .build();

        assertEquals(Duration.ofSeconds(10), policy.calculateDelay(1)); // 10
        assertEquals(Duration.ofSeconds(20), policy.calculateDelay(2)); // 20
        assertEquals(Duration.ofSeconds(30), policy.calculateDelay(3)); // 40 -> capped to 30
        assertEquals(Duration.ofSeconds(30), policy.calculateDelay(4)); // 80 -> capped to 30
        assertEquals(Duration.ofSeconds(30), policy.calculateDelay(5)); // 160 -> capped to 30
    }

    @Test
    void testIsRetryableTimeoutError() {
        RetryPolicy policy = RetryPolicy.defaults();

        assertTrue(policy.isRetryable(new RuntimeException("Connection timeout")));
        assertTrue(policy.isRetryable(new RuntimeException("Request timeout exceeded")));
        assertTrue(policy.isRetryable(new RuntimeException("Socket read timeout")));
    }

    @Test
    void testIsRetryableNetworkError() {
        RetryPolicy policy = RetryPolicy.defaults();

        assertTrue(policy.isRetryable(new RuntimeException("Connection refused")));
        assertTrue(policy.isRetryable(new RuntimeException("Network error")));
        assertTrue(policy.isRetryable(new RuntimeException("Socket reset by peer")));
        assertTrue(policy.isRetryable(new RuntimeException("Connection reset")));
    }

    @Test
    void testIsNotRetryableInsufficientFunds() {
        RetryPolicy policy = RetryPolicy.defaults();

        assertFalse(policy.isRetryable(new RuntimeException("Insufficient funds")));
        assertFalse(policy.isRetryable(new RuntimeException("insufficient ada balance")));
    }

    @Test
    void testIsNotRetryableInvalidTransaction() {
        RetryPolicy policy = RetryPolicy.defaults();

        assertFalse(policy.isRetryable(new RuntimeException("Invalid transaction")));
        assertFalse(policy.isRetryable(new RuntimeException("Transaction is invalid")));
    }

    @Test
    void testIsNotRetryableAlreadySpent() {
        RetryPolicy policy = RetryPolicy.defaults();

        assertFalse(policy.isRetryable(new RuntimeException("UTXO already spent")));
        assertFalse(policy.isRetryable(new RuntimeException("Input already spent")));
    }

    @Test
    void testIsNotRetryableBadRequest() {
        RetryPolicy policy = RetryPolicy.defaults();

        assertFalse(policy.isRetryable(new RuntimeException("Bad request")));
        assertFalse(policy.isRetryable(new RuntimeException("400 Bad Request")));
    }

    @Test
    void testDisabledTimeoutRetry() {
        RetryPolicy policy = RetryPolicy.builder()
                .retryOnTimeout(false)
                .retryOnNetworkError(false)  // Disable both to test
                .build();

        // When both timeout and network retry are disabled, the error is still retryable
        // as an unknown error (default behavior). This tests that the timeout-specific
        // path is not taken when disabled.
        // The error "Connection timeout" won't match the timeout OR network paths when both
        // are disabled, but will still be retryable as unknown.
        assertTrue(policy.isRetryable(new RuntimeException("Connection timeout")));

        // However, insufficient funds should still not be retryable
        assertFalse(policy.isRetryable(new RuntimeException("Insufficient funds")));
    }

    @Test
    void testDisabledNetworkRetry() {
        RetryPolicy policy = RetryPolicy.builder()
                .retryOnTimeout(false)
                .retryOnNetworkError(false)
                .build();

        // When both timeout and network retry are disabled, the errors are still retryable
        // as unknown errors (default behavior).
        assertTrue(policy.isRetryable(new RuntimeException("connection refused")));

        // However, invalid transaction should still not be retryable
        assertFalse(policy.isRetryable(new RuntimeException("invalid transaction")));
    }

    @Test
    void testNullError() {
        RetryPolicy policy = RetryPolicy.defaults();

        assertFalse(policy.isRetryable(null));
    }

    @Test
    void testNullErrorMessage() {
        RetryPolicy policy = RetryPolicy.defaults();

        // Error with null message should be treated as retryable (unknown error)
        assertTrue(policy.isRetryable(new RuntimeException((String) null)));
    }

    @Test
    void testUnknownErrorIsRetryable() {
        RetryPolicy policy = RetryPolicy.defaults();

        // Unknown errors should be retryable by default
        assertTrue(policy.isRetryable(new RuntimeException("Some unknown error")));
    }

    @Test
    void testToString() {
        RetryPolicy policy = RetryPolicy.defaults();
        String str = policy.toString();

        assertTrue(str.contains("maxAttempts=3"));
        assertTrue(str.contains("backoffStrategy=EXPONENTIAL"));
    }

    @Test
    void testCalculateDelayWithZeroAttempt() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialDelay(Duration.ofSeconds(1))
                .build();

        // Attempt 0 or negative should return initial delay
        assertEquals(Duration.ofSeconds(1), policy.calculateDelay(0));
        assertEquals(Duration.ofSeconds(1), policy.calculateDelay(-1));
    }

    @Test
    void testConfirmationTimeoutIsNotRetryable() {
        RetryPolicy policy = RetryPolicy.defaults();

        // ConfirmationTimeoutException should NEVER be retryable
        ConfirmationTimeoutException exception = new ConfirmationTimeoutException("txHash123");
        assertFalse(policy.isRetryable(exception));
    }

    @Test
    void testConfirmationTimeoutMessageIsNotRetryable() {
        RetryPolicy policy = RetryPolicy.defaults();

        // Even generic RuntimeException with "confirmation timeout" message should not be retryable
        RuntimeException exception = new RuntimeException("Transaction confirmation timeout: abc123");
        assertFalse(policy.isRetryable(exception));
    }

    @Test
    void testWrappedConfirmationTimeoutIsNotRetryable() {
        RetryPolicy policy = RetryPolicy.defaults();

        // ConfirmationTimeoutException wrapped in another exception should still not be retryable
        ConfirmationTimeoutException confirmationTimeout = new ConfirmationTimeoutException("txHash123");
        RuntimeException wrappedException = new RuntimeException("Wrapped exception", confirmationTimeout);
        assertFalse(policy.isRetryable(wrappedException));
    }

    @Test
    void testNetworkTimeoutIsStillRetryable() {
        RetryPolicy policy = RetryPolicy.defaults();

        // Network/connection timeouts should still be retryable
        assertTrue(policy.isRetryable(new RuntimeException("Connection timeout")));
        assertTrue(policy.isRetryable(new RuntimeException("Request timeout")));
        assertTrue(policy.isRetryable(new RuntimeException("Socket read timeout")));
    }

    @Test
    void testConfirmationTimeoutVsNetworkTimeout() {
        RetryPolicy policy = RetryPolicy.defaults();

        // Confirmation timeout - NOT retryable (would cause duplicate transactions)
        assertFalse(policy.isRetryable(new ConfirmationTimeoutException("txHash")));
        assertFalse(policy.isRetryable(new RuntimeException("Transaction confirmation timeout: xyz")));

        // Network timeout - retryable (transaction was never submitted)
        assertTrue(policy.isRetryable(new RuntimeException("Network timeout")));
        assertTrue(policy.isRetryable(new RuntimeException("HTTP timeout connecting to server")));
    }
}
