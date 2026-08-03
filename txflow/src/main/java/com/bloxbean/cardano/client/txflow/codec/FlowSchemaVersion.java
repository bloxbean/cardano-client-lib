package com.bloxbean.cardano.client.txflow.codec;

/** Serialized TxFlow contract understood by {@link TxFlowCodec}. */
public enum FlowSchemaVersion {
    /** Compatibility format identified by the historical {@code version: 1.0} field. */
    LEGACY("1.0"),
    /** First explicitly versioned portable contract. */
    V1ALPHA1("txflow.cardano-client.dev/v1alpha1");

    private final String identifier;

    FlowSchemaVersion(String identifier) {
        this.identifier = identifier;
    }

    /**
     * Returns the identifier emitted in the corresponding document version field.
     *
     * @return legacy version or portable API-version identifier
     */
    public String getIdentifier() {
        return identifier;
    }
}
