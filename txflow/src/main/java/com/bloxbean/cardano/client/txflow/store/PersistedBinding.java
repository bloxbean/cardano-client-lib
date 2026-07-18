package com.bloxbean.cardano.client.txflow.store;

/**
 * Durable representation of a resolved execution parameter.
 *
 * <p>Non-sensitive values may be stored directly in {@code nonSensitiveValue}. Sensitive values
 * instead use an opaque {@code secureValueRef} resolved through an application-owned
 * {@link SecureBindingResolver}; TxFlow snapshots must not contain the secret itself. The
 * fingerprint supports identity checks without disclosure, and {@code redactedDisplay} is the
 * only representation intended for diagnostics.</p>
 *
 * <p>The record describes the two storage forms but does not itself enforce that exactly one of
 * {@code nonSensitiveValue} and {@code secureValueRef} is populated. Producers and store adapters
 * must preserve that invariant.</p>
 *
 * @param parameterName name declared by the portable flow definition
 * @param parameterType stable declared parameter type
 * @param nonSensitiveValue directly persisted value, or {@code null} for a sensitive binding
 * @param secureValueRef opaque secure-store reference, or {@code null} for a non-sensitive binding
 * @param valueFingerprint fingerprint of the resolved value
 * @param redactedDisplay safe diagnostic representation
 */
public record PersistedBinding(String parameterName, String parameterType,
                               Object nonSensitiveValue, String secureValueRef,
                               String valueFingerprint, String redactedDisplay) {
}
