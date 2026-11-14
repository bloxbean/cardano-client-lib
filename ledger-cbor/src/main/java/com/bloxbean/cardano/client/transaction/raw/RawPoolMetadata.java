package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;

/**
 * A pool's metadata as received, {@code [url, bytes]}.
 */
public final class RawPoolMetadata {
    static final RecordSpec RECORD = RecordSpec.positional("pool_metadata", FieldType.URL, FieldType.BYTES);

    private final PositionalRecord record;

    private RawPoolMetadata(PositionalRecord record) {
        this.record = record;
    }

    static RawPoolMetadata read(CborSpan span, boolean exact) {
        return new RawPoolMetadata(PositionalRecord.of(RECORD, span, exact));
    }

    public String url() {
        return RawItems.text(record.value(FieldType.URL));
    }

    public byte[] hash() {
        return RawItems.bytes(record.value(FieldType.BYTES));
    }
}
