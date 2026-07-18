package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.result.FlowResult;

/**
 * Internal execution adapter used by the stream worker.
 * <p>
 * Production streams delegate to {@link com.bloxbean.cardano.client.txflow.exec.FlowExecutor};
 * tests can inject lightweight runners without constructing backend services.
 */
interface FlowExecutionRunner extends AutoCloseable {
    /**
     * Execute one generated bounded flow.
     *
     * @param flow generated flow to execute
     * @return bounded flow execution result
     */
    FlowResult execute(TxFlow flow);

    /**
     * Release runner resources.
     */
    @Override
    default void close() {
    }
}
