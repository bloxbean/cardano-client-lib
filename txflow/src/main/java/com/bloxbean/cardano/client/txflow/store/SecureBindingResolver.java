package com.bloxbean.cardano.client.txflow.store;

/**
 * Application-owned secret-store adapter for recovering a sensitive parameter binding.
 *
 * <p>TxFlow persists only the opaque reference in {@link PersistedBinding}; secret storage,
 * access control, rotation, and audit policy remain the embedding application's responsibility.
 * Implementations should return a value compatible with the binding's declared parameter type
 * and must not expose the value through logs or exception messages.</p>
 */
@FunctionalInterface
public interface SecureBindingResolver {
    /**
     * Resolves a reference previously written to {@link PersistedBinding#secureValueRef()}.
     *
     * @param secureValueReference opaque application-owned secret reference
     * @return resolved parameter value
     */
    Object resolve(String secureValueReference);
}
