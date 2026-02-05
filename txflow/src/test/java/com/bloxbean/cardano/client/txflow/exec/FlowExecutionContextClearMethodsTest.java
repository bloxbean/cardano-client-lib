package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the new clear methods in FlowExecutionContext used for rollback recovery.
 */
class FlowExecutionContextClearMethodsTest {

    private FlowExecutionContext context;

    @BeforeEach
    void setUp() {
        context = new FlowExecutionContext("test-flow");
    }

    @Test
    void testClearStepResult() {
        // Record some step results
        FlowStepResult result1 = FlowStepResult.success("step1", "txhash1", Collections.emptyList(), Collections.emptyList());
        FlowStepResult result2 = FlowStepResult.success("step2", "txhash2", Collections.emptyList(), Collections.emptyList());
        FlowStepResult result3 = FlowStepResult.success("step3", "txhash3", Collections.emptyList(), Collections.emptyList());

        context.recordStepResult("step1", result1);
        context.recordStepResult("step2", result2);
        context.recordStepResult("step3", result3);

        // Verify all recorded
        assertEquals(3, context.getCompletedStepCount());
        assertTrue(context.getStepResult("step1").isPresent());
        assertTrue(context.getStepResult("step2").isPresent());
        assertTrue(context.getStepResult("step3").isPresent());

        // Clear step2
        context.clearStepResult("step2");

        // Verify only step2 is cleared
        assertEquals(2, context.getCompletedStepCount());
        assertTrue(context.getStepResult("step1").isPresent());
        assertFalse(context.getStepResult("step2").isPresent());
        assertTrue(context.getStepResult("step3").isPresent());
    }

    @Test
    void testClearStepResult_NonExistent() {
        // Clear a non-existent step should not throw
        assertDoesNotThrow(() -> context.clearStepResult("nonexistent"));
        assertEquals(0, context.getCompletedStepCount());
    }

    @Test
    void testClearAllStepResults() {
        // Record some step results
        FlowStepResult result1 = FlowStepResult.success("step1", "txhash1", Collections.emptyList(), Collections.emptyList());
        FlowStepResult result2 = FlowStepResult.success("step2", "txhash2", Collections.emptyList(), Collections.emptyList());
        FlowStepResult result3 = FlowStepResult.success("step3", "txhash3", Collections.emptyList(), Collections.emptyList());

        context.recordStepResult("step1", result1);
        context.recordStepResult("step2", result2);
        context.recordStepResult("step3", result3);

        // Verify all recorded
        assertEquals(3, context.getCompletedStepCount());

        // Clear all
        context.clearAllStepResults();

        // Verify all cleared
        assertEquals(0, context.getCompletedStepCount());
        assertFalse(context.getStepResult("step1").isPresent());
        assertFalse(context.getStepResult("step2").isPresent());
        assertFalse(context.getStepResult("step3").isPresent());
    }

    @Test
    void testClearAllStepResults_Empty() {
        // Clear on empty context should not throw
        assertDoesNotThrow(() -> context.clearAllStepResults());
        assertEquals(0, context.getCompletedStepCount());
    }

    @Test
    void testGetCompletedStepCount() {
        assertEquals(0, context.getCompletedStepCount());

        FlowStepResult result = FlowStepResult.success("step1", "txhash", Collections.emptyList(), Collections.emptyList());
        context.recordStepResult("step1", result);

        assertEquals(1, context.getCompletedStepCount());

        FlowStepResult result2 = FlowStepResult.success("step2", "txhash2", Collections.emptyList(), Collections.emptyList());
        context.recordStepResult("step2", result2);

        assertEquals(2, context.getCompletedStepCount());
    }

    @Test
    void testClearAndRerecord() {
        // Record a step
        FlowStepResult result1 = FlowStepResult.success("step1", "txhash1", Collections.emptyList(), Collections.emptyList());
        context.recordStepResult("step1", result1);

        assertEquals("txhash1", context.getStepTransactionHash("step1").orElse(null));

        // Clear and re-record with different hash (simulating rebuild)
        context.clearStepResult("step1");
        FlowStepResult result2 = FlowStepResult.success("step1", "txhash1-new", Collections.emptyList(), Collections.emptyList());
        context.recordStepResult("step1", result2);

        assertEquals("txhash1-new", context.getStepTransactionHash("step1").orElse(null));
        assertEquals(1, context.getCompletedStepCount());
    }

    @Test
    void testVariablesNotAffectedByClear() {
        context.setVariable("key1", "value1");
        context.setVariable("key2", "value2");

        FlowStepResult result = FlowStepResult.success("step1", "txhash", Collections.emptyList(), Collections.emptyList());
        context.recordStepResult("step1", result);

        // Clear all step results
        context.clearAllStepResults();

        // Variables should still be intact
        assertEquals("value1", context.getVariable("key1"));
        assertEquals("value2", context.getVariable("key2"));
    }

    @Test
    void testSharedDataNotAffectedByClear() {
        context.setSharedData("sharedKey", "sharedValue");

        FlowStepResult result = FlowStepResult.success("step1", "txhash", Collections.emptyList(), Collections.emptyList());
        context.recordStepResult("step1", result);

        // Clear all step results
        context.clearAllStepResults();

        // Shared data should still be intact
        assertEquals("sharedValue", context.getSharedData("sharedKey", String.class).orElse(null));
    }
}
