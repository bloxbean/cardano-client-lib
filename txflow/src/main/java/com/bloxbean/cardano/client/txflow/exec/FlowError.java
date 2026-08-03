package com.bloxbean.cardano.client.txflow.exec;

/**
 * Stable error information returned by the execution API.
 *
 * <p>The error is a transport-friendly description rather than the original
 * exception graph. {@code code} is intended for programmatic handling, while
 * {@code category} supports broader policy and reporting decisions.</p>
 *
 * @param code stable TxFlow error code
 * @param category broad failure category
 * @param message human-readable diagnostic message
 * @param stepId step associated with the failure, or {@code null} for a
 *               flow-wide failure
 * @param retryable whether a caller may safely retry according to server policy. For
 *                  {@code TXFLOW_RECOVERY_REQUIRED} errors this is <em>per-request</em>
 *                  truth: {@code true} only when re-driving the same request attaches to
 *                  the existing execution — a durable store, or an explicit idempotency
 *                  key held in the in-memory claim map. A keyless request on a
 *                  non-durable engine gets {@code false}: a re-start there is a fresh
 *                  execution and can pay twice. Even when {@code true}, this never
 *                  licenses rebuilding the payment as new work — reconcile first.
 */
public record FlowError(String code, FlowErrorCategory category, String message,
                        String stepId, boolean retryable) {
}
