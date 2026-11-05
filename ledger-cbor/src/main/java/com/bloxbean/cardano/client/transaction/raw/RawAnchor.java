package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

/**
 * An anchor as received, {@code [anchor_url : url, anchor_data_hash : hash32]}.
 */
public final class RawAnchor {
    static final RecordSpec RECORD = RecordSpec.positional("anchor", FieldType.URL, FieldType.HASH32);

    private final CborSpan span;
    private final PositionalRecord record;

    private RawAnchor(CborSpan span, PositionalRecord record) {
        this.span = span;
        this.record = record;
    }

    /**
     * @param span an anchor as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of two items
     */
    public static RawAnchor of(CborSpan span) {
        return new RawAnchor(span, PositionalRecord.of(RECORD, span, true));
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
