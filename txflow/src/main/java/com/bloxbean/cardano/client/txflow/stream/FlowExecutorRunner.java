package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutor;
import com.bloxbean.cardano.client.txflow.result.FlowResult;

/**
 * {@link FlowExecutionRunner} backed by a real {@link FlowExecutor}.
 */
final class FlowExecutorRunner implements FlowExecutionRunner {
    private final FlowExecutor flowExecutor;

    /**
     * Create a runner for the configured flow executor.
     *
     * @param flowExecutor executor used to run generated bounded flows
     */
    FlowExecutorRunner(FlowExecutor flowExecutor) {
        this.flowExecutor = flowExecutor;
    }

    @Override
    public FlowResult execute(TxFlow flow) {
        return flowExecutor.executeSync(flow);
    }

    @Override
    public void close() {
        flowExecutor.close();
    }
}
