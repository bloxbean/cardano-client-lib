package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

/**
 * An anchor as received, {@code [anchor_url : url, anchor_data_hash : hash32]}.
 */
public final class RawAnchor {
    static final RecordSpec RECORD = RecordSpec.positional("anchor", FieldType.URL, FieldType.HASH32);

    private final CborSpan span;
    private final PositionalRecord record;
    private final Era era;

    private RawAnchor(CborSpan span, PositionalRecord record, Era era) {
        this.span = span;
        this.record = record;
        this.era = era;
    }

    /**
     * @param span an anchor as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of two items
     */
    public static RawAnchor of(CborSpan span) {
        return of(span, null);
    }

    // The anchor of a record read in an era.
    static RawAnchor of(CborSpan span, Era era) {
        return new RawAnchor(span, PositionalRecord.of(RECORD, span, true), era);
    }

    /**
     * The era of the record the anchor was read from, which says whether it may be indefinite
     * ({@link RawItems#indefiniteRecordsAllowed(Era)}).
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the era, or null if it was read without one
     */
    public Era era() {
        return era;
    }

    /**
     * @return the anchor as encoded
     */
    public CborSpan span() {
        return span;
    }

    public String url() {
        return RawItems.text(record.value(FieldType.URL));
    }

    /**
     * @return the data hash, of any length
     */
    public byte[] dataHash() {
        return RawItems.bytes(record.value(FieldType.HASH32));
    }
}
