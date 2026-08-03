package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.ChainingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChainingStrategyTest {
    @Test
    void dispatchesEachModeToExactlyOneOperation() {
        assertEquals("sequential", execute(ChainingMode.SEQUENTIAL));
        assertEquals("pipelined", execute(ChainingMode.PIPELINED));
        assertEquals("batch", execute(ChainingMode.BATCH));
    }

    private String execute(ChainingMode mode) {
        return ChainingStrategy.forMode(mode).execute(
                () -> "sequential", () -> "pipelined", () -> "batch");
    }
}
