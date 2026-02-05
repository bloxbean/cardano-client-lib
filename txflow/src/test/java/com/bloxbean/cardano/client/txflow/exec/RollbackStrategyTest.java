package com.bloxbean.cardano.client.txflow.exec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RollbackStrategyTest {

    @Test
    void testAllStrategiesExist() {
        assertEquals(4, RollbackStrategy.values().length);
        assertNotNull(RollbackStrategy.FAIL_IMMEDIATELY);
        assertNotNull(RollbackStrategy.NOTIFY_ONLY);
        assertNotNull(RollbackStrategy.REBUILD_FROM_FAILED);
        assertNotNull(RollbackStrategy.REBUILD_ENTIRE_FLOW);
    }

    @Test
    void testValueOfFromString() {
        assertEquals(RollbackStrategy.FAIL_IMMEDIATELY, RollbackStrategy.valueOf("FAIL_IMMEDIATELY"));
        assertEquals(RollbackStrategy.NOTIFY_ONLY, RollbackStrategy.valueOf("NOTIFY_ONLY"));
        assertEquals(RollbackStrategy.REBUILD_FROM_FAILED, RollbackStrategy.valueOf("REBUILD_FROM_FAILED"));
        assertEquals(RollbackStrategy.REBUILD_ENTIRE_FLOW, RollbackStrategy.valueOf("REBUILD_ENTIRE_FLOW"));
    }

    @Test
    void testFailImmediatelyIsFirst() {
        // FAIL_IMMEDIATELY should be the recommended default (first in enum)
        assertEquals(0, RollbackStrategy.FAIL_IMMEDIATELY.ordinal());
    }

    @Test
    void testStrategyOrdering() {
        // Verify expected ordering for documentation purposes
        assertEquals(0, RollbackStrategy.FAIL_IMMEDIATELY.ordinal());
        assertEquals(1, RollbackStrategy.NOTIFY_ONLY.ordinal());
        assertEquals(2, RollbackStrategy.REBUILD_FROM_FAILED.ordinal());
        assertEquals(3, RollbackStrategy.REBUILD_ENTIRE_FLOW.ordinal());
    }
}
