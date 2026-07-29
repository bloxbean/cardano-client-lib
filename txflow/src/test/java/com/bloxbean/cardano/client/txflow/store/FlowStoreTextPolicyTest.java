package com.bloxbean.cardano.client.txflow.store;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowEventType;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowStoreTextPolicyTest {
    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");

    @Test
    void ordinaryUnicodeRemainsPortableStoreText() {
        assertDoesNotThrow(() -> new FlowExecutionSnapshot(
                "execution-付款-🚀", "definition-å", "request-ß",
                FlowExecutionState.CREATED, 0, 0, 0, NOW, Map.of()));
        assertDoesNotThrow(() -> new FlowEvent(
                1, "execution-付款-🚀", FlowEventType.EXECUTION_STARTED, NOW,
                "step-α", "transaction-β", Map.of()));
    }

    @Test
    void persistedColumnValuesRejectNulAtTheirPublicBoundaries() {
        assertThrows(IllegalArgumentException.class, () -> new FlowExecutionSnapshot(
                "execution\u0000id", "definition", "request", FlowExecutionState.CREATED,
                0, 0, 0, NOW, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new FlowExecutionSnapshot(
                "execution", "definition\u0000fingerprint", "request",
                FlowExecutionState.CREATED, 0, 0, 0, NOW, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionLease(
                "execution", "owner\u0000token", 1, NOW));
        assertThrows(IllegalArgumentException.class, () -> new ResourceLease(
                "resource\u0000id", "execution", "owner", 1, NOW));
        assertThrows(IllegalArgumentException.class, () -> new FlowEvent(
                1, "execution", FlowEventType.EXECUTION_STARTED, NOW,
                "step\u0000id", null, Map.of()));
    }

    @Test
    void byteLimitsTreatAsciiAndMultibyteUnicodeConsistently() {
        String exactly512Bytes = "界".repeat(170) + "ab";
        String tooLarge = exactly512Bytes + "c";

        assertEquals(exactly512Bytes, FlowStoreTextPolicy.requireIdentifier(
                exactly512Bytes, "executionId",
                FlowStoreTextPolicy.MAX_EXECUTION_ID_BYTES));
        assertThrows(IllegalArgumentException.class,
                () -> FlowStoreTextPolicy.requireIdentifier(
                        tooLarge, "executionId",
                        FlowStoreTextPolicy.MAX_EXECUTION_ID_BYTES));
        assertThrows(IllegalArgumentException.class,
                () -> FlowStoreTextPolicy.requireIdentifier(
                        "é".repeat(128), "idempotency namespace",
                        FlowStoreTextPolicy.MAX_NAMESPACE_BYTES));
        assertThrows(IllegalArgumentException.class,
                () -> FlowStoreTextPolicy.requireIdentifier(
                        "malformed-" + Character.toString((char) 0xd800), "executionId",
                        FlowStoreTextPolicy.MAX_EXECUTION_ID_BYTES));
    }
}
