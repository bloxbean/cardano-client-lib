package com.bloxbean.cardano.client.txflow.stream;

/**
 * Write-ahead binding from an item to its deterministic execution identity.
 * <p>
 * The binding is written with state {@code DISPATCHING} before the engine is
 * asked to start the execution, and confirmed with a {@link BindingOutcome}
 * afterwards. Because the execution id is derived from the item's idempotency
 * claim, a {@code MATCHED} start outcome always refers to exactly the
 * execution this binding names.
 *
 * @param executionId deterministic engine execution identity
 * @param flowId generated single-step flow identity
 * @param stepId step identity carrying the item's transaction
 * @param laneName user-facing label of the lane the execution runs on
 */
public record TxStreamBinding(String executionId, String flowId, String stepId, String laneName) {
    /**
     * Validates the binding identities.
     *
     * @param executionId non-blank execution identity
     * @param flowId non-blank flow identity
     * @param stepId non-blank step identity
     * @param laneName non-blank lane label
     */
    public TxStreamBinding {
        requireNonBlank(executionId, "executionId");
        requireNonBlank(flowId, "flowId");
        requireNonBlank(stepId, "stepId");
        requireNonBlank(laneName, "laneName");
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
    }
}
