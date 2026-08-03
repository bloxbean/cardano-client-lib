package com.bloxbean.cardano.vds.jmt.store;

/**
 * Thrown when a fail-fast JMT access lease conflicts with an active operation.
 */
public final class JmtConcurrentMutationException extends IllegalStateException {

    private final JmtAccessMode requestedMode;
    private final String requestedOperation;

    JmtConcurrentMutationException(JmtAccessMode requestedMode,
                                   String requestedOperation,
                                   String activeOperation) {
        super("Cannot acquire JMT " + requestedMode + " lease for '" + requestedOperation
                + "'; incompatible access is active: " + activeOperation);
        this.requestedMode = requestedMode;
        this.requestedOperation = requestedOperation;
    }

    public JmtAccessMode requestedMode() {
        return requestedMode;
    }

    public String requestedOperation() {
        return requestedOperation;
    }
}
