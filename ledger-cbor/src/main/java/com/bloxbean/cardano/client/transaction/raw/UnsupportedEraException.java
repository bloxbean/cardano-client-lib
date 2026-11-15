package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.exception.CborRuntimeException;

/**
 * A block's envelope names an era this reader has no row for: Byron, Dijkstra or an era it does not know
 * ({@link EraSpec}). The bytes may be fine: {@link RawBlock#readAs} reads a block with a named era's layouts, if that
 * is what the caller wants.
 */
public final class UnsupportedEraException extends CborRuntimeException {
    private final long eraIndex;

    UnsupportedEraException(long eraIndex, String message) {
        super(message);
        this.eraIndex = eraIndex;
    }

    /**
     * @return the era index, as {@link BlockEnvelope#eraIndex()} returns it
     */
    public long eraIndex() {
        return eraIndex;
    }
}
