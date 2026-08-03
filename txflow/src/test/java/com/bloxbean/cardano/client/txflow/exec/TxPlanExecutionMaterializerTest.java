package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TxPlanExecutionMaterializerTest {
    @Test
    void mutableSourceIsSnapshottedOncePerExecution() {
        TxPlan source = new TxPlan().addVariable("value", "initial");
        TxPlanExecutionMaterializer materializer = new TxPlanExecutionMaterializer();
        FlowExecutionContext firstExecution = new FlowExecutionContext("flow", Map.of());

        TxPlan firstAttempt = materializer.materialize(source, Map.of(), firstExecution);
        source.addVariable("value", "mutated");
        TxPlan retryAttempt = materializer.materialize(source, Map.of(), firstExecution);
        TxPlan laterExecution = materializer.materialize(
                source, Map.of(), new FlowExecutionContext("flow", Map.of()));

        assertEquals("initial", firstAttempt.getVariables().get("value"));
        assertEquals("initial", retryAttempt.getVariables().get("value"));
        assertEquals("mutated", laterExecution.getVariables().get("value"));
    }
}
