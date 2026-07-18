package com.bloxbean.cardano.client.txflow.store.rdbms;

/**
 * Startup behavior for the TxFlow relational schema.
 */
public enum SchemaManagement {
    /** Validate migration history and apply supported forward migrations. */
    MIGRATE,
    /** Require the current compatible schema without changing it. */
    VALIDATE,
    /** Perform no startup schema operation. */
    NONE
}
