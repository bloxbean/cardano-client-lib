package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.txflow.exec.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.exec.RollbackStrategy;
import lombok.Builder;
import lombok.Getter;

/**
 * Optional flow-level execution settings carried by a {@link TxFlow}.
 * <p>
 * These settings describe how a flow should be executed when the executor has
 * not provided an explicit override. Null fields mean the setting was not
 * provided by the flow.
 */
@Getter
@Builder(toBuilder = true)
public class FlowExecutionSettings {

    private static final FlowExecutionSettings EMPTY = FlowExecutionSettings.builder().build();

    private final ChainingMode chainingMode;
    private final ConfirmationConfig confirmationConfig;
    private final RollbackStrategy rollbackStrategy;
    private final RetryPolicy retryPolicy;

    public static FlowExecutionSettings empty() {
        return EMPTY;
    }

    public boolean hasAnySetting() {
        return chainingMode != null
                || confirmationConfig != null
                || rollbackStrategy != null
                || retryPolicy != null;
    }
}
