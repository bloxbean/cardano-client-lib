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
 * @param retryable whether a caller may safely retry according to server policy
 */
public record FlowError(String code, FlowErrorCategory category, String message,
                        String stepId, boolean retryable) {
}
