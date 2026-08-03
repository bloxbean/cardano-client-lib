package com.bloxbean.cardano.vds.jmt.store;

/**
 * Thrown when the persisted latest root no longer matches a commit's expected base root.
 */
public final class JmtWriteConflictException extends IllegalStateException {

    public JmtWriteConflictException(String message) {
        super(message);
    }
}
